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
import ticketing.domain.payment.client.dto.KakaoPayReadyRequest;
import ticketing.domain.payment.client.dto.KakaoPayReadyResponse;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.util.RedisLockService;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFacadeService {

	private static final Duration LOCK_TTL = Duration.ofSeconds(5);
	private static final Duration CREATE_LOCK_TTL = Duration.ofSeconds(5);

	private final OrderCommandService orderCommandService;
	private final KakaoPayApiClient kakaoPayApiClient;
	private final RedisLockService redisLockService;

	/**
	 * userId 기반 멱등처리
	 * 주문 생성: PENDING 주문 생성(tx) -> PG ready → Payment 저장(tx)
	 */
	public CreateDTO.Result create(CreateDTO.Command command) {

		String lockKey = OrderRedisKeys.createLockKey(command.getUserId());
		boolean acquired = redisLockService.tryLock(lockKey, command.getUserId().toString(), CREATE_LOCK_TTL);

		if (!acquired) {
			throw new GeneralException(OrderErrorCode.ORDER_IN_PROGRESS);
		}

		Long orderId;
		try {
			orderId = orderCommandService.createPendingOrder(command);
		} finally {
			redisLockService.releaseLock(lockKey);
		}

		try {
			KakaoPayReadyResponse readyResponse = kakaoPayApiClient.ready(
				KakaoPayReadyRequest.builder()
					.orderId(orderId)
					.build()
			);

			orderCommandService.attachPayment(
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
			orderCommandService.expirePendingOrder(orderId);
			throw e;
		}
	}

	/**
	 * orderId 기반 멱등 처리
	 * 주문 확정: 검증(tx) -> PG approve 출금 -> 로컬 확정(tx).
	 */
	public ConfirmDTO.Result confirm(ConfirmDTO.Command command) {

		String lockKey = OrderRedisKeys.confirmLockKey(command.getOrderId());
		boolean acquired = redisLockService.tryLock(lockKey, command.getUserId().toString(), LOCK_TTL);

		if (!acquired) {
			throw new GeneralException(OrderErrorCode.ORDER_IN_PROGRESS);
		}

		try {
			ConfirmDTO.Validated validated = orderCommandService.validateConfirm(command);

			// 실제 출금 - 외부 API 호출 (트랜잭션 밖)
			KakaoPayApproveResponse approveResponse = kakaoPayApiClient.approve(
				KakaoPayApproveRequest.builder()
					.tid(validated.getTid())
					.pgToken(command.getPgToken())
					.amount(validated.getAmount())
					.build()
			);

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
	 * 		이떄, confirm()과 같은 Lock 키를 써서 같은 주문에 대한 확정, 취소가 동시에 진행되지 않도록 방어
	 */
	public CancelDTO.Result cancelAll(CancelDTO.Command command) {

		String lockKey = OrderRedisKeys.confirmLockKey(command.getOrderId());
		boolean acquired = redisLockService.tryLock(lockKey, command.getUserId().toString(), LOCK_TTL);

		if (!acquired) {
			throw new GeneralException(OrderErrorCode.ORDER_IN_PROGRESS);
		}

		try {
			CancelDTO.Validated validated = orderCommandService.validateCancel(command);

			// 실제 환불 - 외부 API 호출 (트랜잭션 밖)
			kakaoPayApiClient.cancel(validated.getTid(), validated.getAmount());

			try {
				return orderCommandService.completeCancel(command);
			} catch (Exception e) {
				log.error(
					"[OrderFacadeService] - cancelAll() PG 환불은 성공했으나 로컬 취소 반영에 실패했습니다. orderId={}, tid={}, amount={}",
					command.getOrderId(), validated.getTid(), validated.getAmount(), e
				);
				throw e;
			}
		} finally {
			redisLockService.releaseLock(lockKey);
		}
	}
}
