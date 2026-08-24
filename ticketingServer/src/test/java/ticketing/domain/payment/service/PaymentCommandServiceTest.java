package ticketing.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.order.order.entity.Order;
import ticketing.domain.order.order.repository.OrderRepository;
import ticketing.domain.order.orderitem.entity.OrderItem;
import ticketing.domain.order.orderitem.repository.OrderItemRepository;
import ticketing.domain.payment.entity.Payment;
import ticketing.domain.payment.exception.PaymentErrorCode;
import ticketing.domain.payment.repository.PaymentRepository;
import ticketing.fixture.OrderFixture;
import ticketing.fixture.PaymentFixture;
import ticketing.fixture.ScheduleSeatFixture;
import ticketing.fixture.UserFixture;
import ticketing.global.apiPayload.exception.GeneralException;

/**
 * PaymentCommandService의 결제 준비 저장과 대사(reconcile) 상태 가드·상태 전이를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceTest {

	private static final int AMOUNT = 50_000;

	@InjectMocks
	private PaymentCommandService paymentCommandService;

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private OrderItemRepository orderItemRepository;

	@Nested
	@DisplayName("createPayment")
	class CreatePayment {

		@Test
		void 주문_금액을_그대로_받아_READY_상태로_저장된다() {
			// given
			Order order = OrderFixture.pendingOrder(1L, UserFixture.user(1L), AMOUNT);
			given(orderRepository.findById(1L)).willReturn(Optional.of(order));

			// when
			paymentCommandService.createPayment(
				1L, Payment.PaymentMethod.KAKAO_PAY, PaymentFixture.TID, PaymentFixture.REDIRECT_URL);

			// then
			ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
			verify(paymentRepository).save(paymentCaptor.capture());
			assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(Payment.PaymentStatus.READY);
			assertThat(paymentCaptor.getValue().getTotalPrice()).isEqualTo(AMOUNT);
			assertThat(paymentCaptor.getValue().getTid()).isEqualTo(PaymentFixture.TID);
		}
	}

	@Nested
	@DisplayName("prepareReadyReconcile")
	class PrepareReadyReconcile {

		@Test
		void READY_상태가_아니면_NOT_RECONCILE_TARGET_예외가_발생한다() {
			// given
			Order order = OrderFixture.confirmedOrder(1L, UserFixture.user(1L), AMOUNT);
			given(paymentRepository.findByOrderId(1L))
				.willReturn(Optional.of(PaymentFixture.approvedPayment(1L, order, AMOUNT)));

			// when
			Throwable thrown = catchThrowable(() -> paymentCommandService.prepareReadyReconcile(1L));

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(PaymentErrorCode.NOT_RECONCILE_TARGET);
		}
	}

	@Nested
	@DisplayName("completeReadyReconcile")
	class CompleteReadyReconcile {

		@Test
		void READY_상태가_아니면_RECONCILE_STATE_CHANGED_예외가_발생한다() {
			// given
			Order order = OrderFixture.confirmedOrder(1L, UserFixture.user(1L), AMOUNT);
			given(paymentRepository.findByOrderId(1L))
				.willReturn(Optional.of(PaymentFixture.approvedPayment(1L, order, AMOUNT)));

			// when
			Throwable thrown = catchThrowable(() -> paymentCommandService.completeReadyReconcile(1L));

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(PaymentErrorCode.RECONCILE_STATE_CHANGED);
		}

		@Test
		void 대사가_끝나면_결제와_주문항목과_주문이_모두_만료로_전이된다() {
			// given
			Order order = OrderFixture.pendingOrder(1L, UserFixture.user(1L), AMOUNT);
			Payment payment = PaymentFixture.readyPayment(1L, order, AMOUNT);
			OrderItem orderItem = OrderFixture.pendingOrderItem(
				1L, order, ScheduleSeatFixture.availableScheduleSeat(10L), AMOUNT);
			given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));
			given(orderItemRepository.findAllByOrderIdWithScheduleSeat(1L)).willReturn(List.of(orderItem));

			// when
			paymentCommandService.completeReadyReconcile(1L);

			// then
			assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.CANCELLED);
			assertThat(orderItem.getStatus()).isEqualTo(OrderItem.Status.EXPIRED);
			assertThat(order.getOrderStatus()).isEqualTo(Order.OrderStatus.EXPIRED);
		}
	}

	@Nested
	@DisplayName("completeCancelReconcile")
	class CompleteCancelReconcile {

		@Test
		void CANCEL_REQUESTED_상태가_아니면_RECONCILE_STATE_CHANGED_예외가_발생한다() {
			// given
			Order order = OrderFixture.confirmedOrder(1L, UserFixture.user(1L), AMOUNT);
			given(paymentRepository.findByOrderId(1L))
				.willReturn(Optional.of(PaymentFixture.approvedPayment(1L, order, AMOUNT)));

			// when
			Throwable thrown = catchThrowable(() -> paymentCommandService.completeCancelReconcile(1L));

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(PaymentErrorCode.RECONCILE_STATE_CHANGED);
		}

		@Test
		void 대사가_끝나면_좌석과_주문항목과_결제와_주문이_모두_취소로_전이된다() {
			// given
			Order order = OrderFixture.confirmedOrder(1L, UserFixture.user(1L), AMOUNT);
			Payment payment = PaymentFixture.cancelRequestedPayment(1L, order, AMOUNT);
			ScheduleSeat scheduleSeat = ScheduleSeatFixture.soldScheduleSeat(10L);
			OrderItem orderItem = OrderFixture.confirmedOrderItem(1L, order, scheduleSeat, AMOUNT);
			given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));
			given(orderItemRepository.findAllByOrderIdWithScheduleSeat(1L)).willReturn(List.of(orderItem));

			// when
			paymentCommandService.completeCancelReconcile(1L);

			// then
			assertThat(scheduleSeat.getSeatStatus()).isEqualTo(ScheduleSeat.SeatStatus.AVAILABLE);
			assertThat(orderItem.getStatus()).isEqualTo(OrderItem.Status.CANCELLED);
			assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.CANCELLED);
			assertThat(order.getOrderStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
		}
	}
}
