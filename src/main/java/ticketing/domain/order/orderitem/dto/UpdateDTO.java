package ticketing.domain.order.orderitem.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.domain.order.orderitem.entity.OrderItem;

public class UpdateDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		@NotNull
		Long orderId;
		@NotNull
		Long scheduleSeatId;
		@NotNull
		Integer price;

		public Command toCommand() {
			return Command.builder()
				.orderId(orderId)
				.scheduleSeatId(scheduleSeatId)
				.price(price)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Command {
		Long orderId;
		Long scheduleSeatId;
		Integer price;
	}

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
