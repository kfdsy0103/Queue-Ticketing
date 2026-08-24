package ticketing.fixture;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.test.util.ReflectionTestUtils;

import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.order.order.dto.CancelDTO;
import ticketing.domain.order.order.dto.ConfirmDTO;
import ticketing.domain.order.order.dto.CreateDTO;
import ticketing.domain.order.order.dto.FindDTO;
import ticketing.domain.order.order.entity.Order;
import ticketing.domain.order.orderitem.entity.OrderItem;
import ticketing.domain.payment.entity.Payment;
import ticketing.domain.user.entity.User;

public final class OrderFixture {

	private OrderFixture() {
	}

	public static Order pendingOrder(Long id, User user, int totalPrice) {
		return Order.builder()
			.id(id)
			.user(user)
			.orderStatus(Order.OrderStatus.PENDING)
			.totalPrice(totalPrice)
			.build();
	}

	public static Order confirmedOrder(Long id, User user, int totalPrice) {
		return Order.builder()
			.id(id)
			.user(user)
			.orderStatus(Order.OrderStatus.CONFIRMED)
			.totalPrice(totalPrice)
			.build();
	}

	/**
	 * createdAt은 JPA Auditing 필드라 빌더에 노출되지 않아 리플렉션으로 주입한다.
	 */
	public static Order pendingOrderCreatedAt(Long id, User user, int totalPrice, LocalDateTime createdAt) {
		Order order = pendingOrder(id, user, totalPrice);
		ReflectionTestUtils.setField(order, "createdAt", createdAt);
		return order;
	}

	public static OrderItem pendingOrderItem(Long id, Order order, ScheduleSeat scheduleSeat, int price) {
		return OrderItem.builder()
			.id(id)
			.order(order)
			.scheduleSeat(scheduleSeat)
			.price(price)
			.status(OrderItem.Status.PENDING)
			.build();
	}

	public static OrderItem confirmedOrderItem(Long id, Order order, ScheduleSeat scheduleSeat, int price) {
		return OrderItem.builder()
			.id(id)
			.order(order)
			.scheduleSeat(scheduleSeat)
			.price(price)
			.status(OrderItem.Status.CONFIRMED)
			.confirmedScheduleSeatId(scheduleSeat.getId())
			.build();
	}

	public static CreateDTO.Command createCommand(Long userId, List<Long> scheduleSeatIds) {
		return CreateDTO.Command.builder()
			.userId(userId)
			.scheduleSeatIds(scheduleSeatIds)
			.paymentMethod(Payment.PaymentMethod.KAKAO_PAY)
			.build();
	}

	public static ConfirmDTO.Command confirmCommand(Long userId, Long orderId) {
		return ConfirmDTO.Command.builder()
			.userId(userId)
			.orderId(orderId)
			.pgToken("pgToken_test")
			.build();
	}

	public static CancelDTO.Command cancelCommand(Long orderId, Long userId) {
		return CancelDTO.Command.of(orderId, userId);
	}

	public static FindDTO.Command findCommand(Long orderId, Long userId) {
		return FindDTO.Command.builder()
			.orderId(orderId)
			.userId(userId)
			.build();
	}
}
