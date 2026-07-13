package ticketing.domain.order.order.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.domain.order.order.entity.Order;

public class FindDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		@NotNull
		Long orderId;

		public Command toCommand(Long userId) {
			return Command.builder()
				.userId(userId)
				.orderId(orderId)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Command {
		Long userId;
		Long orderId;
	}

	@Getter
	@Builder
	public static class Result {
		Long orderId;
		Order.OrderStatus orderStatus;
		int totalPrice;
		LocalDateTime createdAt;

		public Response toResponse() {
			return Response.builder()
				.orderId(orderId)
				.orderStatus(orderStatus)
				.totalPrice(totalPrice)
				.createdAt(createdAt)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		Long orderId;
		Order.OrderStatus orderStatus;
		int totalPrice;
		LocalDateTime createdAt;
	}
}
