package ticketing.domain.payment.service;

import java.time.Duration;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.order.order.constants.OrderRedisKeys;
import ticketing.domain.order.order.exception.OrderErrorCode;
import ticketing.domain.payment.client.KakaoPayApiClient;
import ticketing.domain.payment.client.dto.KakaoPayOrderRequest;
import ticketing.domain.payment.client.dto.KakaoPayOrderResponse;
import ticketing.domain.payment.client.enums.KakaoPayStatus;
import ticketing.domain.payment.dto.ReconcileDTO;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.util.RedisLockService;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFacadeService {

	// 실제 유저의 confirm/cancelAll API 요청과 경합 방지
	private static final Duration LOCK_TTL = Duration.ofSeconds(5);

	private final PaymentCommandService paymentCommandService;
	private final KakaoPayApiClient kakaoPayApiClient;
	private final RedisLockService redisLockService;

	/**
	 * 	PG 승인 이후 서버 크래시 등으로 로컬에 반영되지 못한 결제 건을 PG 상태 재조회로 환불 처리합니다.
	 */
	public void reconcileReadyPayment(Long orderId) {

		String lockKey = OrderRedisKeys.confirmLockKey(orderId);
		boolean acquired = redisLockService.tryLock(lockKey, LOCK_TTL);

		if (!acquired) {
			throw new GeneralException(OrderErrorCode.ORDER_IN_PROGRESS);
		}

		try {
			ReconcileDTO.Prepared target = paymentCommandService.prepareReadyReconcile(orderId);

			// 실제 PG 서버에 상태 조회
			KakaoPayOrderResponse orderResponse = kakaoPayApiClient.order(
				KakaoPayOrderRequest.builder()
					.tid(target.getTid())
					.amount(target.getAmount())
					.build()
			);

			// 실제로는 출금까지 됐던 건이므로 돈을 돌려준다
			if (orderResponse.getStatus() == KakaoPayStatus.SUCCESS_PAYMENT) {
				kakaoPayApiClient.cancel(target.getTid(), target.getAmount());
				log.warn(
					"[PaymentFacadeService] - reconcileReadyPayment() 실제 승인 완료 상태였으나, 로컬 커밋 미반영 상태로 감지되어 환불 처리되었습니다. orderId={}, tid={}",
					orderId, target.getTid()
				);
			} else {
				log.info(
					"[PaymentFacadeService] - reconcileReadyPayment() 실제 미승인 상태로 만료되어 결제 건을 종료 처리했습니다. orderId={}, tid={}, pgStatus={}",
					orderId, target.getTid(), orderResponse.getStatus()
				);
			}

			paymentCommandService.completeReadyReconcile(orderId);
		} finally {
			redisLockService.releaseLock(lockKey);
		}
	}

	/**
	 * orderId 기반 멱등 처리
	 * 		환불을 요청했으나 그 결과를 로컬에 반영하지 못한 채 남은 결제 건을 복구합니다.
	 * 		1. PG에도 환불이 반영되지 않은 경우	-> 환불을 재요청한 뒤 로컬 취소 확정
	 * 		2. PG에는 이미 반영된 경우			-> 로컬만 취소 확정
	 */
	public void reconcileCancelRequestedPayment(Long orderId) {

		String lockKey = OrderRedisKeys.cancelLockKey(orderId);
		boolean acquired = redisLockService.tryLock(lockKey, LOCK_TTL);

		if (!acquired) {
			throw new GeneralException(OrderErrorCode.ORDER_IN_PROGRESS);
		}

		try {
			ReconcileDTO.Prepared target = paymentCommandService.prepareCancelReconcile(orderId);

			KakaoPayOrderResponse orderResponse = kakaoPayApiClient.order(
				KakaoPayOrderRequest.builder()
					.tid(target.getTid())
					.amount(target.getAmount())
					.build()
			);

			// PG에도 환불이 반영되지 않았다면 아예 호출이 실패했던 것이므로 재요청한다
			if (orderResponse.getStatus() != KakaoPayStatus.CANCEL_PAYMENT) {
				kakaoPayApiClient.cancel(target.getTid(), target.getAmount());
				log.warn(
					"[PaymentFacadeService] - reconcileCancelRequestedPayment() 환불이 반영되지 않은 상태로 감지되어 재요청했습니다. orderId={}, tid={}, pgStatus={}",
					orderId, target.getTid(), orderResponse.getStatus()
				);
			}

			paymentCommandService.completeCancelReconcile(orderId);
		} finally {
			redisLockService.releaseLock(lockKey);
		}
	}
}
