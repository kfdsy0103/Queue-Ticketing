package ticketing.domain.order.order.service;

import java.time.Duration;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.order.order.constants.OrderRedisKeys;
import ticketing.domain.order.order.dto.CancelDTO;
import ticketing.domain.order.order.dto.ConfirmDTO;
import ticketing.domain.order.order.dto.CreateDTO;
import ticketing.domain.order.order.exception.OrderErrorCode;
import ticketing.domain.payment.client.KakaoPayApiClient;
import ticketing.domain.payment.client.dto.KakaoPayApproveRequest;
import ticketing.domain.payment.client.dto.KakaoPayApproveResponse;
import ticketing.domain.payment.client.dto.KakaoPayOrderRequest;
import ticketing.domain.payment.client.dto.KakaoPayOrderResponse;
import ticketing.domain.payment.client.dto.KakaoPayReadyRequest;
import ticketing.domain.payment.client.dto.KakaoPayReadyResponse;
import ticketing.domain.payment.client.enums.KakaoPayStatus;
import ticketing.domain.payment.service.PaymentCommandService;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.util.RedisLockService;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFacadeService {

	private static final Duration LOCK_TTL = Duration.ofSeconds(5);
	private static final Duration CREATE_LOCK_TTL = Duration.ofSeconds(5);

	private final OrderCommandService orderCommandService;
	private final PaymentCommandService paymentCommandService;
	private final KakaoPayApiClient kakaoPayApiClient;
	private final RedisLockService redisLockService;

	/**
	 * userId 기반 멱등처리
	 * 주문 생성: PENDING 주문 생성(tx) -> PG ready → Payment 저장(tx)
	 */
	public CreateDTO.Result create(CreateDTO.Command command) {

		String lockKey = OrderRedisKeys.createLockKey(command.getUserId());
		boolean acquired = redisLockService.tryLock(lockKey, CREATE_LOCK_TTL);

		if (!acquired) {
			throw new GeneralException(OrderErrorCode.ORDER_IN_PROGRESS);
		}

		Long orderId = null;
		try {
			orderId = orderCommandService.createOrder(command);

			KakaoPayReadyResponse readyResponse = kakaoPayApiClient.ready(
				KakaoPayReadyRequest.builder()
					.orderId(orderId)
					.build()
			);

			paymentCommandService.createPayment(
				orderId,
				command.getPaymentMethod(),
				readyResponse.getTid(),
				readyResponse.getRedirectUrl()
			);

			return CreateDTO.Result.builder()
				.orderId(orderId)
				.redirectUrl(readyResponse.getRedirectUrl())
				.build();
		} catch (Exception e) {
			// createOrder() 자체가 실패하면 orderId가 없다. 여기서 findById(null)이 터지면 원래 원인이 덮인다
			if (orderId != null) {
				orderCommandService.expireOrder(orderId);
			}
			throw e;
		} finally {
			redisLockService.releaseLock(lockKey);
		}
	}

	/**
	 * orderId 기반 멱등 처리
	 * 주문 확정: 검증(tx) -> PG approve 출금 -> 로컬 확정(tx).
	 */
	public ConfirmDTO.Result confirm(ConfirmDTO.Command command) {

		String lockKey = OrderRedisKeys.confirmLockKey(command.getOrderId());
		boolean acquired = redisLockService.tryLock(lockKey, LOCK_TTL);

		if (!acquired) {
			throw new GeneralException(OrderErrorCode.ORDER_IN_PROGRESS);
		}

		try {
			ConfirmDTO.Validated validated = orderCommandService.validateConfirm(command);

			// 실제 출금 - 외부 API 호출 (트랜잭션 밖)
			KakaoPayApproveResponse approveResponse;
			try {
				approveResponse = kakaoPayApiClient.approve(
					KakaoPayApproveRequest.builder()
						.tid(validated.getTid())
						.pgToken(command.getPgToken())
						.amount(validated.getAmount())
						.build()
				);
			} catch (Exception approveFailure) {	// 타임아웃 등을 이유로 approve()에 실패한 경우

				// 진짜 저쪽 서버에 반영이 되었는지 상태 조회 API 호출
				KakaoPayOrderResponse orderResponse;
				try {
					orderResponse = kakaoPayApiClient.order(
						KakaoPayOrderRequest.builder()
							.tid(validated.getTid())
							.amount(validated.getAmount())
							.build()
					);
				} catch (Exception orderFailure) {
					approveFailure.addSuppressed(orderFailure);
					log.error(
						"[OrderFacadeService] - confirm() 승인 응답도 상태 조회도 실패했습니다. 스케쥴러 대사에 맡깁니다. orderId={}, tid={}",
						command.getOrderId(), validated.getTid(), approveFailure
					);
					throw approveFailure;
				}

				// 실제로 승인되지 않았으므로 원래 실패를 그대로 전파
				if (orderResponse.getStatus() != KakaoPayStatus.SUCCESS_PAYMENT) {
					throw approveFailure;
				}

				log.warn(
					"[OrderFacadeService] - confirm() 승인 응답은 받지 못했지만 PG에서는 승인된 상태로 확인되어 확정을 이어갑니다. orderId={}, tid={}, aid={}",
					command.getOrderId(), orderResponse.getTid(), orderResponse.getAid()
				);

				approveResponse = KakaoPayApproveResponse.builder()
					.aid(orderResponse.getAid())
					.tid(orderResponse.getTid())
					.paymentMethodType(orderResponse.getPaymentMethodType())
					.amount(orderResponse.getAmount())
					.approvedAt(orderResponse.getApprovedAt())
					.build();
			}

			try {
				return orderCommandService.completeConfirm(command, approveResponse);
			} catch (Exception e) {
				// 출금은 됐으나 로컬 확정 실패 -> 이미 나간 결제를 환불
				kakaoPayApiClient.cancel(approveResponse.getTid(), approveResponse.getAmount());
				throw e;
			}
		} finally {
			redisLockService.releaseLock(lockKey);
		}
	}

	/**
	 * orderId 기반 멱등 처리
	 * 		검증(tx) -> PG 환불 -> 로컬 확정(tx).
	 * 		이떄, 취소 대사 스케쥴러와 같은 Lock 키를 써서 같은 주문에 대한 환불이 중복 진행되지 않도록 방어
	 */
	public CancelDTO.Result cancelAll(CancelDTO.Command command) {

		String lockKey = OrderRedisKeys.cancelLockKey(command.getOrderId());
		boolean acquired = redisLockService.tryLock(lockKey, LOCK_TTL);

		if (!acquired) {
			throw new GeneralException(OrderErrorCode.ORDER_IN_PROGRESS);
		}

		try {
			CancelDTO.Prepared prepared = orderCommandService.prepareCancel(command);

			// 실제 환불 - 외부 API 호출 (트랜잭션 밖)
			kakaoPayApiClient.cancel(prepared.getTid(), prepared.getAmount());

			try {
				return orderCommandService.completeCancel(command);
			} catch (Exception e) {
				log.error(
					"[OrderFacadeService] - cancelAll() PG 환불은 성공했으나 로컬 취소 반영에 실패했습니다. orderId={}, tid={}, amount={}",
					command.getOrderId(), prepared.getTid(), prepared.getAmount(), e
				);
				throw e;
			}
		} finally {
			redisLockService.releaseLock(lockKey);
		}
	}

	/**
	 * orderId 기반 멱등 처리
	 * 		PG 호출에 실패해 결제가 붙지 못한 채 PENDING으로 남은 주문을 만료시킵니다. (외부 PG 호출 없음)
	 * 		이떄, confirm()과 같은 Lock 키를 써서 진행 중인 확정 건을 만료시키지 않도록 방어
	 */
	public boolean expireOrphanedOrder(Long orderId) {

		String lockKey = OrderRedisKeys.confirmLockKey(orderId);
		boolean acquired = redisLockService.tryLock(lockKey, LOCK_TTL);

		if (!acquired) {
			throw new GeneralException(OrderErrorCode.ORDER_IN_PROGRESS);
		}

		try {
			return orderCommandService.expireOrder(orderId);
		} finally {
			redisLockService.releaseLock(lockKey);
		}
	}
}
