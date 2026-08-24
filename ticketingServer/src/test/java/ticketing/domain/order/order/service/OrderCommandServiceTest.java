package ticketing.domain.order.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ticketing.domain.concert.concert.entity.Concert;
import ticketing.domain.concert.concertschedule.entity.ConcertSchedule;
import ticketing.domain.concert.scheduleprice.repository.SchedulePriceRepository;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.concert.scheduleseat.exception.ScheduleSeatErrorCode;
import ticketing.domain.concert.scheduleseat.repository.ScheduleSeatRepository;
import ticketing.domain.order.order.dto.CancelDTO;
import ticketing.domain.order.order.dto.ConfirmDTO;
import ticketing.domain.order.order.entity.Order;
import ticketing.domain.order.order.exception.OrderErrorCode;
import ticketing.domain.order.order.repository.OrderRepository;
import ticketing.domain.order.orderitem.entity.OrderItem;
import ticketing.domain.order.orderitem.repository.OrderItemRepository;
import ticketing.domain.payment.entity.Payment;
import ticketing.domain.payment.exception.PaymentErrorCode;
import ticketing.domain.payment.repository.PaymentRepository;
import ticketing.domain.seatgrade.entity.SeatGrade;
import ticketing.domain.user.entity.User;
import ticketing.domain.user.repository.UserRepository;
import ticketing.domain.venue.seat.entity.Seat;
import ticketing.domain.venue.venue.entity.Venue;
import ticketing.fixture.ConcertFixture;
import ticketing.fixture.OrderFixture;
import ticketing.fixture.PaymentFixture;
import ticketing.fixture.ScheduleSeatFixture;
import ticketing.fixture.UserFixture;
import ticketing.fixture.VenueFixture;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.util.RedisUtil;

/**
 * OrderCommandService의 주문 생성·만료·확정·취소 핵심 검증 분기와 상태 전이를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class OrderCommandServiceTest {

	private static final int SEAT_PRICE = 50_000;

	@InjectMocks
	private OrderCommandService orderCommandService;

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private ScheduleSeatRepository scheduleSeatRepository;

	@Mock
	private OrderItemRepository orderItemRepository;

	@Mock
	private SchedulePriceRepository schedulePriceRepository;

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private RedisUtil redisUtil;

	@Captor
	private ArgumentCaptor<List<OrderItem>> orderItemsCaptor;

	private static ScheduleSeat availableSeat(Long scheduleSeatId) {
		return ScheduleSeatFixture.availableScheduleSeat(scheduleSeatId, concertSchedule(), seat(scheduleSeatId));
	}

	private static ScheduleSeat soldSeat(Long scheduleSeatId) {
		return ScheduleSeatFixture.soldScheduleSeat(scheduleSeatId, concertSchedule(), seat(scheduleSeatId));
	}

	private static ConcertSchedule concertSchedule() {
		Concert concert = ConcertFixture.concert(1L, VenueFixture.venue(1L));
		return ConcertFixture.concertSchedule(1L, concert);
	}

	private static Seat seat(Long seatId) {
		Venue venue = VenueFixture.venue(1L);
		SeatGrade seatGrade = VenueFixture.seatGrade(1L, "VIP");
		return VenueFixture.seat(seatId, venue, seatGrade, "A" + seatId);
	}

	@Nested
	@DisplayName("createOrder")
	class CreateOrder {

		@Test
		void 이미_판매된_좌석이_포함되면_NOT_AVAILABLE_SEAT_예외가_발생한다() {
			// given
			given(userRepository.findById(1L)).willReturn(Optional.of(UserFixture.user(1L)));
			given(scheduleSeatRepository.findAllByIdInWithScheduleAndSeatGrade(List.of(10L)))
				.willReturn(List.of(soldSeat(10L)));

			// when
			Throwable thrown = catchThrowable(
				() -> orderCommandService.createOrder(OrderFixture.createCommand(1L, List.of(10L))));

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(ScheduleSeatErrorCode.NOT_AVAILABLE_SEAT);
			verify(orderRepository, never()).save(any());
		}

		@Test
		void 본인이_점유한_좌석이_아니면_NOT_OCCUPIED_BY_USER_예외가_발생한다() {
			// given
			given(userRepository.findById(1L)).willReturn(Optional.of(UserFixture.user(1L)));
			given(scheduleSeatRepository.findAllByIdInWithScheduleAndSeatGrade(List.of(10L)))
				.willReturn(List.of(availableSeat(10L)));
			given(redisUtil.<Long>execute(any(), anyList(), any())).willReturn(0L);

			// when
			Throwable thrown = catchThrowable(
				() -> orderCommandService.createOrder(OrderFixture.createCommand(1L, List.of(10L))));

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(ScheduleSeatErrorCode.NOT_OCCUPIED_BY_USER);
			verify(orderRepository, never()).save(any());
		}

		@Test
		void 검증을_통과하면_좌석_가격_합계로_PENDING_주문과_주문항목이_저장된다() {
			// given
			User user = UserFixture.user(1L);
			ScheduleSeat first = availableSeat(10L);
			ScheduleSeat second = availableSeat(11L);
			given(userRepository.findById(1L)).willReturn(Optional.of(user));
			given(scheduleSeatRepository.findAllByIdInWithScheduleAndSeatGrade(List.of(10L, 11L)))
				.willReturn(List.of(first, second));
			given(redisUtil.<Long>execute(any(), anyList(), any())).willReturn(1L);
			given(schedulePriceRepository.findAllByConcertScheduleIdIn(any()))
				.willReturn(List.of(ConcertFixture.schedulePrice(
					1L, first.getConcertSchedule(), first.getSeat().getSeatGrade(), SEAT_PRICE)));

			// when
			orderCommandService.createOrder(OrderFixture.createCommand(1L, List.of(10L, 11L)));

			// then
			ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
			verify(orderRepository).save(orderCaptor.capture());
			assertThat(orderCaptor.getValue().getUser()).isEqualTo(user);
			assertThat(orderCaptor.getValue().getOrderStatus()).isEqualTo(Order.OrderStatus.PENDING);
			assertThat(orderCaptor.getValue().getTotalPrice()).isEqualTo(SEAT_PRICE * 2);

			verify(orderItemRepository).saveAll(orderItemsCaptor.capture());
			assertThat(orderItemsCaptor.getValue()).hasSize(2);
			assertThat(orderItemsCaptor.getValue()).allSatisfy(orderItem -> {
				assertThat(orderItem.getStatus()).isEqualTo(OrderItem.Status.PENDING);
				assertThat(orderItem.getPrice()).isEqualTo(SEAT_PRICE);
			});
		}
	}

	@Nested
	@DisplayName("expireOrder")
	class ExpireOrder {

		@Test
		void PENDING이_아닌_주문이면_false를_반환하고_상태를_바꾸지_않는다() {
			// given
			Order order = OrderFixture.confirmedOrder(1L, UserFixture.user(1L), SEAT_PRICE);
			given(orderRepository.findById(1L)).willReturn(Optional.of(order));

			// when
			boolean expired = orderCommandService.expireOrder(1L);

			// then
			assertThat(expired).isFalse();
			assertThat(order.getOrderStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
			verify(orderItemRepository, never()).findAllByOrderIdWithScheduleSeat(any());
		}

		@Test
		void PENDING_주문이면_주문과_주문항목이_EXPIRED로_전이된다() {
			// given
			Order order = OrderFixture.pendingOrder(1L, UserFixture.user(1L), SEAT_PRICE);
			OrderItem orderItem = OrderFixture.pendingOrderItem(1L, order, availableSeat(10L), SEAT_PRICE);
			given(orderRepository.findById(1L)).willReturn(Optional.of(order));
			given(orderItemRepository.findAllByOrderIdWithScheduleSeat(1L)).willReturn(List.of(orderItem));

			// when
			boolean expired = orderCommandService.expireOrder(1L);

			// then
			assertThat(expired).isTrue();
			assertThat(order.getOrderStatus()).isEqualTo(Order.OrderStatus.EXPIRED);
			assertThat(orderItem.getStatus()).isEqualTo(OrderItem.Status.EXPIRED);
		}
	}

	@Nested
	@DisplayName("validateConfirm")
	class ValidateConfirm {

		@Test
		void 이미_확정된_주문이면_ALREADY_PAID_예외가_발생한다() {
			// given
			Order order = OrderFixture.confirmedOrder(1L, UserFixture.user(1L), SEAT_PRICE);
			given(orderRepository.findById(1L)).willReturn(Optional.of(order));

			// when
			Throwable thrown = catchThrowable(
				() -> orderCommandService.validateConfirm(OrderFixture.confirmCommand(1L, 1L)));

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(PaymentErrorCode.ALREADY_PAID);
		}

		@Test
		void 결제_금액과_주문_금액이_다르면_AMOUNT_MISMATCH_예외가_발생한다() {
			// given
			Order order = OrderFixture.pendingOrder(1L, UserFixture.user(1L), SEAT_PRICE);
			given(orderRepository.findById(1L)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(1L))
				.willReturn(Optional.of(PaymentFixture.readyPayment(1L, order, SEAT_PRICE + 1)));

			// when
			Throwable thrown = catchThrowable(
				() -> orderCommandService.validateConfirm(OrderFixture.confirmCommand(1L, 1L)));

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(PaymentErrorCode.AMOUNT_MISMATCH);
		}

		@Test
		void 검증을_통과하면_tid와_금액이_반환된다() {
			// given
			Order order = OrderFixture.pendingOrder(1L, UserFixture.user(1L), SEAT_PRICE);
			given(orderRepository.findById(1L)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(1L))
				.willReturn(Optional.of(PaymentFixture.readyPayment(1L, order, SEAT_PRICE)));
			given(orderItemRepository.findAllByOrderIdWithScheduleSeat(1L))
				.willReturn(List.of(OrderFixture.pendingOrderItem(1L, order, availableSeat(10L), SEAT_PRICE)));
			given(redisUtil.<Long>execute(any(), anyList(), any())).willReturn(1L);

			// when
			ConfirmDTO.Validated validated = orderCommandService.validateConfirm(OrderFixture.confirmCommand(1L, 1L));

			// then
			assertThat(validated.getTid()).isEqualTo(PaymentFixture.TID);
			assertThat(validated.getAmount()).isEqualTo(SEAT_PRICE);
		}
	}

	@Nested
	@DisplayName("completeConfirm")
	class CompleteConfirm {

		@Test
		void PG_승인_금액이_결제_금액과_다르면_AMOUNT_MISMATCH_예외가_발생한다() {
			// given
			Order order = OrderFixture.pendingOrder(1L, UserFixture.user(1L), SEAT_PRICE);
			given(orderRepository.findById(1L)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(1L))
				.willReturn(Optional.of(PaymentFixture.readyPayment(1L, order, SEAT_PRICE)));

			// when
			Throwable thrown = catchThrowable(() -> orderCommandService.completeConfirm(
				OrderFixture.confirmCommand(1L, 1L), PaymentFixture.approveResponse(SEAT_PRICE + 1)));

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(PaymentErrorCode.AMOUNT_MISMATCH);
			assertThat(order.getOrderStatus()).isEqualTo(Order.OrderStatus.PENDING);
		}

		@Test
		void 확정되면_결제와_주문항목과_좌석과_주문이_모두_전이된다() {
			// given
			Order order = OrderFixture.pendingOrder(1L, UserFixture.user(1L), SEAT_PRICE);
			Payment payment = PaymentFixture.readyPayment(1L, order, SEAT_PRICE);
			ScheduleSeat scheduleSeat = availableSeat(10L);
			OrderItem orderItem = OrderFixture.pendingOrderItem(1L, order, scheduleSeat, SEAT_PRICE);
			given(orderRepository.findById(1L)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));
			given(orderItemRepository.findAllByOrderIdWithScheduleSeat(1L)).willReturn(List.of(orderItem));

			// when
			ConfirmDTO.Result result = orderCommandService.completeConfirm(
				OrderFixture.confirmCommand(1L, 1L), PaymentFixture.approveResponse(SEAT_PRICE));

			// then
			assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.APPROVED);
			assertThat(orderItem.getStatus()).isEqualTo(OrderItem.Status.CONFIRMED);
			assertThat(scheduleSeat.getSeatStatus()).isEqualTo(ScheduleSeat.SeatStatus.SOLD);
			assertThat(order.getOrderStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
			assertThat(result.getOrderId()).isEqualTo(1L);
			assertThat(result.getAid()).isEqualTo(PaymentFixture.AID);
		}
	}

	@Nested
	@DisplayName("prepareCancel")
	class PrepareCancel {

		@Test
		void CONFIRMED가_아닌_주문이면_ORDER_NOT_CONFIRMED_예외가_발생한다() {
			// given
			Order order = OrderFixture.pendingOrder(1L, UserFixture.user(1L), SEAT_PRICE);
			given(orderRepository.findById(1L)).willReturn(Optional.of(order));

			// when
			Throwable thrown = catchThrowable(
				() -> orderCommandService.prepareCancel(OrderFixture.cancelCommand(1L, 1L)));

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(OrderErrorCode.ORDER_NOT_CONFIRMED);
		}

		@Test
		void 검증을_통과하면_결제가_CANCEL_REQUESTED로_전이되고_tid와_금액이_반환된다() {
			// given
			Order order = OrderFixture.confirmedOrder(1L, UserFixture.user(1L), SEAT_PRICE);
			Payment payment = PaymentFixture.approvedPayment(1L, order, SEAT_PRICE);
			given(orderRepository.findById(1L)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

			// when
			CancelDTO.Prepared prepared = orderCommandService.prepareCancel(OrderFixture.cancelCommand(1L, 1L));

			// then
			assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.CANCEL_REQUESTED);
			assertThat(prepared.getTid()).isEqualTo(PaymentFixture.TID);
			assertThat(prepared.getAmount()).isEqualTo(SEAT_PRICE);
		}
	}

	@Nested
	@DisplayName("completeCancel")
	class CompleteCancel {

		@Test
		void 취소가_확정되면_좌석과_주문항목과_결제와_주문이_모두_전이된다() {
			// given
			Order order = OrderFixture.confirmedOrder(1L, UserFixture.user(1L), SEAT_PRICE);
			Payment payment = PaymentFixture.cancelRequestedPayment(1L, order, SEAT_PRICE);
			ScheduleSeat scheduleSeat = soldSeat(10L);
			OrderItem orderItem = OrderFixture.confirmedOrderItem(1L, order, scheduleSeat, SEAT_PRICE);
			given(orderRepository.findById(1L)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));
			given(orderItemRepository.findAllByOrderIdWithScheduleSeat(1L)).willReturn(List.of(orderItem));

			// when
			CancelDTO.Result result = orderCommandService.completeCancel(OrderFixture.cancelCommand(1L, 1L));

			// then
			assertThat(scheduleSeat.getSeatStatus()).isEqualTo(ScheduleSeat.SeatStatus.AVAILABLE);
			assertThat(orderItem.getStatus()).isEqualTo(OrderItem.Status.CANCELLED);
			assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.CANCELLED);
			assertThat(order.getOrderStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
			assertThat(result.getOrderId()).isEqualTo(1L);
		}
	}
}
