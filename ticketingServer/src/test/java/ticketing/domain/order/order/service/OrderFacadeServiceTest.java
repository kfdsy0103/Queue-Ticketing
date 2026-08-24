package ticketing.domain.order.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ticketing.domain.order.order.constants.OrderRedisKeys;
import ticketing.domain.order.order.dto.CancelDTO;
import ticketing.domain.order.order.dto.ConfirmDTO;
import ticketing.domain.order.order.dto.CreateDTO;
import ticketing.domain.order.order.exception.OrderErrorCode;
import ticketing.domain.payment.client.KakaoPayApiClient;
import ticketing.domain.payment.client.enums.KakaoPayStatus;
import ticketing.domain.payment.entity.Payment;
import ticketing.domain.payment.service.PaymentCommandService;
import ticketing.fixture.OrderFixture;
import ticketing.fixture.PaymentFixture;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.util.RedisLockService;

/**
 * OrderFacadeService의 분산 락 획득과 PG 호출 실패 시 보상 로직을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class OrderFacadeServiceTest {

	private static final int AMOUNT = 50_000;

	@InjectMocks
	private OrderFacadeService orderFacadeService;

	@Mock
	private OrderCommandService orderCommandService;

	@Mock
	private PaymentCommandService paymentCommandService;

	@Mock
	private KakaoPayApiClient kakaoPayApiClient;

	@Mock
	private RedisLockService redisLockService;

	private static ConfirmDTO.Validated validated() {
		return ConfirmDTO.Validated.builder()
			.tid(PaymentFixture.TID)
			.amount(AMOUNT)
			.build();
	}

	@Nested
	@DisplayName("create")
	class Create {

		@Test
		void 락_획득에_실패하면_ORDER_IN_PROGRESS_예외가_발생한다() {
			// given
			given(redisLockService.tryLock(eq(OrderRedisKeys.createLockKey(1L)), any(Duration.class)))
				.willReturn(false);

			// when
			Throwable thrown = catchThrowable(
				() -> orderFacadeService.create(OrderFixture.createCommand(1L, List.of(10L))));

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(OrderErrorCode.ORDER_IN_PROGRESS);
			verify(orderCommandService, never()).createOrder(any());
			verify(redisLockService, never()).releaseLock(anyString());
		}

		@Test
		void 정상_생성되면_주문ID와_리다이렉트_URL이_반환되고_락이_해제된다() {
			// given
			String lockKey = OrderRedisKeys.createLockKey(1L);
			given(redisLockService.tryLock(eq(lockKey), any(Duration.class))).willReturn(true);
			given(orderCommandService.createOrder(any())).willReturn(1L);
			given(kakaoPayApiClient.ready(any())).willReturn(PaymentFixture.readyResponse());

			// when
			CreateDTO.Result result = orderFacadeService.create(OrderFixture.createCommand(1L, List.of(10L)));

			// then
			assertThat(result.getOrderId()).isEqualTo(1L);
			assertThat(result.getRedirectUrl()).isEqualTo(PaymentFixture.REDIRECT_URL);
			verify(paymentCommandService).createPayment(
				1L, Payment.PaymentMethod.KAKAO_PAY, PaymentFixture.TID, PaymentFixture.REDIRECT_URL);
			verify(redisLockService).releaseLock(lockKey);
		}

		@Test
		void PG_ready_실패시_생성된_주문이_만료되고_예외가_전파된다() {
			// given
			given(redisLockService.tryLock(any(), any(Duration.class))).willReturn(true);
			given(orderCommandService.createOrder(any())).willReturn(1L);
			willThrow(new IllegalStateException("PG 장애")).given(kakaoPayApiClient).ready(any());

			// when
			Throwable thrown = catchThrowable(
				() -> orderFacadeService.create(OrderFixture.createCommand(1L, List.of(10L))));

			// then
			assertThat(thrown).isInstanceOf(IllegalStateException.class);
			verify(orderCommandService).expireOrder(1L);
		}
	}

	@Nested
	@DisplayName("confirm")
	class Confirm {

		@Test
		void 정상_승인되면_확정_결과가_반환되고_락이_해제된다() {
			// given
			String lockKey = OrderRedisKeys.confirmLockKey(1L);
			ConfirmDTO.Result expected = ConfirmDTO.Result.builder().orderId(1L).amount(AMOUNT).build();
			given(redisLockService.tryLock(eq(lockKey), any(Duration.class))).willReturn(true);
			given(orderCommandService.validateConfirm(any())).willReturn(validated());
			given(kakaoPayApiClient.approve(any())).willReturn(PaymentFixture.approveResponse(AMOUNT));
			given(orderCommandService.completeConfirm(any(), any())).willReturn(expected);

			// when
			ConfirmDTO.Result result = orderFacadeService.confirm(OrderFixture.confirmCommand(1L, 1L));

			// then
			assertThat(result).isSameAs(expected);
			verify(redisLockService).releaseLock(lockKey);
		}

		@Test
		void approve_실패했지만_PG가_승인상태면_상태조회_결과로_확정을_이어간다() {
			// given
			ConfirmDTO.Result expected = ConfirmDTO.Result.builder().orderId(1L).amount(AMOUNT).build();
			given(redisLockService.tryLock(any(), any(Duration.class))).willReturn(true);
			given(orderCommandService.validateConfirm(any())).willReturn(validated());
			willThrow(new IllegalStateException("타임아웃")).given(kakaoPayApiClient).approve(any());
			given(kakaoPayApiClient.order(any()))
				.willReturn(PaymentFixture.orderResponse(KakaoPayStatus.SUCCESS_PAYMENT, AMOUNT));
			given(orderCommandService.completeConfirm(any(), any())).willReturn(expected);

			// when
			ConfirmDTO.Result result = orderFacadeService.confirm(OrderFixture.confirmCommand(1L, 1L));

			// then
			assertThat(result).isSameAs(expected);
		}

		@Test
		void approve_실패하고_PG도_미승인이면_원래_예외가_전파된다() {
			// given
			given(redisLockService.tryLock(any(), any(Duration.class))).willReturn(true);
			given(orderCommandService.validateConfirm(any())).willReturn(validated());
			willThrow(new IllegalStateException("타임아웃")).given(kakaoPayApiClient).approve(any());
			given(kakaoPayApiClient.order(any()))
				.willReturn(PaymentFixture.orderResponse(KakaoPayStatus.FAIL_PAYMENT, AMOUNT));

			// when
			Throwable thrown = catchThrowable(
				() -> orderFacadeService.confirm(OrderFixture.confirmCommand(1L, 1L)));

			// then
			assertThat(thrown).hasMessage("타임아웃");
			verify(orderCommandService, never()).completeConfirm(any(), any());
		}

		@Test
		void 로컬_확정에_실패하면_이미_승인된_결제를_환불하고_예외가_전파된다() {
			// given
			given(redisLockService.tryLock(any(), any(Duration.class))).willReturn(true);
			given(orderCommandService.validateConfirm(any())).willReturn(validated());
			given(kakaoPayApiClient.approve(any())).willReturn(PaymentFixture.approveResponse(AMOUNT));
			willThrow(new IllegalStateException("DB 장애")).given(orderCommandService).completeConfirm(any(), any());

			// when
			Throwable thrown = catchThrowable(
				() -> orderFacadeService.confirm(OrderFixture.confirmCommand(1L, 1L)));

			// then
			assertThat(thrown).isInstanceOf(IllegalStateException.class);
			verify(kakaoPayApiClient).cancel(PaymentFixture.TID, AMOUNT);
		}
	}

	@Nested
	@DisplayName("cancelAll")
	class CancelAll {

		@Test
		void 정상_취소되면_PG_환불_후_취소_결과가_반환되고_락이_해제된다() {
			// given
			String lockKey = OrderRedisKeys.cancelLockKey(1L);
			CancelDTO.Result expected = CancelDTO.Result.builder().orderId(1L).build();
			given(redisLockService.tryLock(eq(lockKey), any(Duration.class))).willReturn(true);
			given(orderCommandService.prepareCancel(any()))
				.willReturn(CancelDTO.Prepared.builder().tid(PaymentFixture.TID).amount(AMOUNT).build());
			given(orderCommandService.completeCancel(any())).willReturn(expected);

			// when
			CancelDTO.Result result = orderFacadeService.cancelAll(OrderFixture.cancelCommand(1L, 1L));

			// then
			assertThat(result).isSameAs(expected);
			verify(kakaoPayApiClient).cancel(PaymentFixture.TID, AMOUNT);
			verify(redisLockService).releaseLock(lockKey);
		}
	}
}
