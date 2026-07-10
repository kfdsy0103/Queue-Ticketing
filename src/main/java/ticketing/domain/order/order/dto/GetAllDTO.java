package ticketing.domain.order.order.dto;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.order.order.entity.Order;

public class GetAllDTO {

	@Getter
	@Builder
	public static class Response {
		Long id;
		Long userId;
		Order.OrderStatus orderStatus;
		int totalPrice;

		public static Response from(Order order) {
			return Response.builder()
				.id(order.getId())
				.userId(order.getUser().getId())
				.orderStatus(order.getOrderStatus())
				.totalPrice(order.getTotalPrice())
				.build();
		}
	}
}
