package ticketing.domain.order.orderitem.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class CancelPartialDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		@NotNull
		Long orderItemId;

		public Command toCommand(Long userId) {
			return Command.builder()
				.orderItemId(orderItemId)
				.userId(userId)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Command {
		Long orderItemId;
		Long userId;
	}

	@Getter
	@Builder
	public static class Result {
		Long orderItemId;
		Long orderId;

		public Response toResponse() {
			return Response.builder()
				.orderItemId(orderItemId)
				.orderId(orderId)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		Long orderItemId;
		Long orderId;
	}
}
