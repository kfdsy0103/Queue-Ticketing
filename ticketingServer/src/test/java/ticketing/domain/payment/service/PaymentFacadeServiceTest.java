package ticketing.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ticketing.domain.order.order.constants.OrderRedisKeys;
import ticketing.domain.order.order.exception.OrderErrorCode;
import ticketing.domain.payment.client.KakaoPayApiClient;
import ticketing.domain.payment.client.enums.KakaoPayStatus;
import ticketing.domain.payment.dto.ReconcileDTO;
import ticketing.fixture.PaymentFixture;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.util.RedisLockService;

/**
 * PaymentFacadeService가 PG 실제 상태에 따라 환불 여부를 갈라 대사를 마무리하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentFacadeServiceTest {

	private static final int AMOUNT = 50_000;

	@InjectMocks
	private PaymentFacadeService paymentFacadeService;

	@Mock
	private PaymentCommandService paymentCommandService;

	@Mock
	private KakaoPayApiClient kakaoPayApiClient;

	@Mock
	private RedisLockService redisLockService;

	private static ReconcileDTO.Prepared prepared() {
		return ReconcileDTO.Prepared.builder()
			.tid(PaymentFixture.TID)
			.amount(AMOUNT)
			.build();
	}

	@Nested
	@DisplayName("reconcileReadyPayment")
	class ReconcileReadyPayment {

		@Test
		void 락_획득에_실패하면_ORDER_IN_PROGRESS_예외가_발생한다() {
			// given
			given(redisLockService.tryLock(eq(OrderRedisKeys.confirmLockKey(1L)), any(Duration.class)))
				.willReturn(false);

			// when
			Throwable thrown = catchThrowable(() -> paymentFacadeService.reconcileReadyPayment(1L));

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(OrderErrorCode.ORDER_IN_PROGRESS);
			verify(paymentCommandService, never()).prepareReadyReconcile(any());
		}

		@Test
		void PG가_승인_상태면_환불한_뒤_로컬을_만료_처리한다() {
			// given
			String lockKey = OrderRedisKeys.confirmLockKey(1L);
			given(redisLockService.tryLock(eq(lockKey), any(Duration.class))).willReturn(true);
			given(paymentCommandService.prepareReadyReconcile(1L)).willReturn(prepared());
			given(kakaoPayApiClient.order(any()))
				.willReturn(PaymentFixture.orderResponse(KakaoPayStatus.SUCCESS_PAYMENT, AMOUNT));

			// when
			paymentFacadeService.reconcileReadyPayment(1L);

			// then
			verify(kakaoPayApiClient).cancel(PaymentFixture.TID, AMOUNT);
			verify(paymentCommandService).completeReadyReconcile(1L);
			verify(redisLockService).releaseLock(lockKey);
		}

		@Test
		void PG가_미승인_상태면_환불하지_않고_로컬만_만료_처리한다() {
			// given
			given(redisLockService.tryLock(any(), any(Duration.class))).willReturn(true);
			given(paymentCommandService.prepareReadyReconcile(1L)).willReturn(prepared());
			given(kakaoPayApiClient.order(any()))
				.willReturn(PaymentFixture.orderResponse(KakaoPayStatus.FAIL_PAYMENT, AMOUNT));

			// when
			paymentFacadeService.reconcileReadyPayment(1L);

			// then
			verify(kakaoPayApiClient, never()).cancel(anyString(), anyInt());
			verify(paymentCommandService).completeReadyReconcile(1L);
		}
	}

	@Nested
	@DisplayName("reconcileCancelRequestedPayment")
	class ReconcileCancelRequestedPayment {

		@Test
		void PG에_환불이_반영되지_않았으면_환불을_재요청한다() {
			// given
			String lockKey = OrderRedisKeys.cancelLockKey(1L);
			given(redisLockService.tryLock(eq(lockKey), any(Duration.class))).willReturn(true);
			given(paymentCommandService.prepareCancelReconcile(1L)).willReturn(prepared());
			given(kakaoPayApiClient.order(any()))
				.willReturn(PaymentFixture.orderResponse(KakaoPayStatus.SUCCESS_PAYMENT, AMOUNT));

			// when
			paymentFacadeService.reconcileCancelRequestedPayment(1L);

			// then
			verify(kakaoPayApiClient).cancel(PaymentFixture.TID, AMOUNT);
			verify(paymentCommandService).completeCancelReconcile(1L);
			verify(redisLockService).releaseLock(lockKey);
		}

		@Test
		void PG에_이미_환불되었으면_재요청_없이_로컬만_확정한다() {
			// given
			given(redisLockService.tryLock(any(), any(Duration.class))).willReturn(true);
			given(paymentCommandService.prepareCancelReconcile(1L)).willReturn(prepared());
			given(kakaoPayApiClient.order(any()))
				.willReturn(PaymentFixture.orderResponse(KakaoPayStatus.CANCEL_PAYMENT, AMOUNT));

			// when
			paymentFacadeService.reconcileCancelRequestedPayment(1L);

			// then
			verify(kakaoPayApiClient, never()).cancel(anyString(), anyInt());
			verify(paymentCommandService).completeCancelReconcile(1L);
		}
	}
}
