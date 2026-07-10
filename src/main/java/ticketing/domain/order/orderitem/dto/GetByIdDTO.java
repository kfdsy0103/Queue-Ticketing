package ticketing.domain.order.orderitem.dto;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.order.orderitem.entity.OrderItem;

public class GetByIdDTO {

	@Getter
	@Builder
	public static class Response {
		Long id;
		Long orderId;
		Long scheduleSeatId;
		int price;

		public static Response from(OrderItem orderItem) {
			return Response.builder()
				.id(orderItem.getId())
				.orderId(orderItem.getOrder().getId())
				.scheduleSeatId(orderItem.getScheduleSeat().getId())
				.price(orderItem.getPrice())
				.build();
		}
	}
}
