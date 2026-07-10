package ticketing.domain.order.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.domain.order.order.entity.Order;

public class CreateDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		@NotNull
		Long userId;
		@NotNull
		Order.OrderStatus orderStatus;
		@NotNull
		Integer totalPrice;

		public Command toCommand() {
			return Command.builder()
				.userId(userId)
				.orderStatus(orderStatus)
				.totalPrice(totalPrice)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Command {
		Long userId;
		Order.OrderStatus orderStatus;
		Integer totalPrice;
	}

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
