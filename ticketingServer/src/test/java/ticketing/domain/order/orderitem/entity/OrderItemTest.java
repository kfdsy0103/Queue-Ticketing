package ticketing.domain.order.orderitem.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.order.order.entity.Order;
import ticketing.fixture.OrderFixture;
import ticketing.fixture.ScheduleSeatFixture;
import ticketing.fixture.UserFixture;

/**
 * OrderItem이 확정 시에만 confirmedScheduleSeatId를 채워, 조건부 유니크 제약으로 좌석 이중 판매를 막는지 검증한다.
 */
class OrderItemTest {

	private static final int PRICE = 10_000;

	private static OrderItem pendingOrderItem(ScheduleSeat scheduleSeat) {
		Order order = OrderFixture.pendingOrder(1L, UserFixture.user(1L), PRICE);
		return OrderFixture.pendingOrderItem(1L, order, scheduleSeat, PRICE);
	}

	@Test
	void 확정되면_상태가_CONFIRMED가_되고_좌석_ID가_유니크_컬럼에_채워진다() {
		// given
		ScheduleSeat scheduleSeat = ScheduleSeatFixture.availableScheduleSeat(10L);
		OrderItem orderItem = pendingOrderItem(scheduleSeat);

		// when
		orderItem.confirm();

		// then
		assertThat(orderItem.getStatus()).isEqualTo(OrderItem.Status.CONFIRMED);
		assertThat(orderItem.getConfirmedScheduleSeatId()).isEqualTo(10L);
	}

	@Test
	void 만료되면_상태가_EXPIRED가_되고_유니크_컬럼이_비워진다() {
		// given
		OrderItem orderItem = pendingOrderItem(ScheduleSeatFixture.availableScheduleSeat(10L));

		// when
		orderItem.expire();

		// then
		assertThat(orderItem.getStatus()).isEqualTo(OrderItem.Status.EXPIRED);
		assertThat(orderItem.getConfirmedScheduleSeatId()).isNull();
	}

	@Test
	void 취소되면_상태가_CANCELLED가_되고_유니크_컬럼이_비워진다() {
		// given
		OrderItem orderItem = pendingOrderItem(ScheduleSeatFixture.availableScheduleSeat(10L));

		// when
		orderItem.cancel();

		// then
		assertThat(orderItem.getStatus()).isEqualTo(OrderItem.Status.CANCELLED);
		assertThat(orderItem.getConfirmedScheduleSeatId()).isNull();
	}

	@Test
	void 확정_후_취소하면_유니크_컬럼이_해제되어_같은_좌석을_다시_팔_수_있다() {
		// given
		OrderItem orderItem = pendingOrderItem(ScheduleSeatFixture.availableScheduleSeat(10L));
		orderItem.confirm();

		// when
		orderItem.cancel();

		// then
		assertThat(orderItem.getStatus()).isEqualTo(OrderItem.Status.CANCELLED);
		assertThat(orderItem.getConfirmedScheduleSeatId()).isNull();
	}

	@Test
	void 확정되지_않은_항목은_유니크_컬럼이_비어_있어_제약에_걸리지_않는다() {
		// when
		OrderItem orderItem = pendingOrderItem(ScheduleSeatFixture.availableScheduleSeat(10L));

		// then
		assertThat(orderItem.getStatus()).isEqualTo(OrderItem.Status.PENDING);
		assertThat(orderItem.getConfirmedScheduleSeatId()).isNull();
	}
}
