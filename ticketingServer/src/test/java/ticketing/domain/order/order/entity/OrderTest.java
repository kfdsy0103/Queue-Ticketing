package ticketing.domain.order.order.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import ticketing.domain.order.order.exception.OrderErrorCode;
import ticketing.fixture.UserFixture;
import ticketing.global.apiPayload.exception.GeneralException;

/**
 * Order의 상태 전이 가드가 허용된 출발 상태에서만 전이를 허용하는지 검증한다.
 */
class OrderTest {

	private static final int TOTAL_PRICE = 10_000;

	private static Order order(Order.OrderStatus status) {
		return Order.builder()
			.id(1L)
			.user(UserFixture.user(1L))
			.orderStatus(status)
			.totalPrice(TOTAL_PRICE)
			.build();
	}

	@Nested
	@DisplayName("confirm")
	class Confirm {

		@Test
		void PENDING_주문은_CONFIRMED로_전이된다() {
			// given
			Order order = order(Order.OrderStatus.PENDING);

			// when
			order.confirm();

			// then
			assertThat(order.getOrderStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
		}

		@ParameterizedTest
		@EnumSource(value = Order.OrderStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
		void PENDING이_아닌_모든_상태에서는_NOT_PENDING_ORDER_예외가_발생한다(Order.OrderStatus status) {
			// given
			Order order = order(status);

			// when
			Throwable thrown = catchThrowable(order::confirm);

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(OrderErrorCode.NOT_PENDING_ORDER);
			assertThat(order.getOrderStatus()).isEqualTo(status);
		}
	}

	@Nested
	@DisplayName("cancel")
	class Cancel {

		@Test
		void CONFIRMED_주문은_CANCELLED로_전이된다() {
			// given
			Order order = order(Order.OrderStatus.CONFIRMED);

			// when
			order.cancel();

			// then
			assertThat(order.getOrderStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
		}

		@ParameterizedTest
		@EnumSource(value = Order.OrderStatus.class, names = "CONFIRMED", mode = EnumSource.Mode.EXCLUDE)
		void CONFIRMED가_아닌_모든_상태에서는_ORDER_NOT_CONFIRMED_예외가_발생한다(Order.OrderStatus status) {
			// given
			Order order = order(status);

			// when
			Throwable thrown = catchThrowable(order::cancel);

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(OrderErrorCode.ORDER_NOT_CONFIRMED);
			assertThat(order.getOrderStatus()).isEqualTo(status);
		}
	}

	@Nested
	@DisplayName("expire")
	class Expire {

		@Test
		void PENDING_주문은_EXPIRED로_전이된다() {
			// given
			Order order = order(Order.OrderStatus.PENDING);

			// when
			order.expire();

			// then
			assertThat(order.getOrderStatus()).isEqualTo(Order.OrderStatus.EXPIRED);
		}

		@ParameterizedTest
		@EnumSource(value = Order.OrderStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
		void PENDING이_아닌_모든_상태에서는_NOT_PENDING_ORDER_예외가_발생한다(Order.OrderStatus status) {
			// given
			Order order = order(status);

			// when
			Throwable thrown = catchThrowable(order::expire);

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(OrderErrorCode.NOT_PENDING_ORDER);
			assertThat(order.getOrderStatus()).isEqualTo(status);
		}
	}

	@Test
	void 취소까지_끝난_주문은_어떤_전이도_되돌릴_수_없다() {
		// given
		Order order = order(Order.OrderStatus.PENDING);
		order.confirm();
		order.cancel();

		// when
		Throwable thrown = catchThrowable(order::confirm);

		// then
		assertThat(order.getOrderStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
		assertThat(((GeneralException)thrown).getCode()).isEqualTo(OrderErrorCode.NOT_PENDING_ORDER);
	}
}
