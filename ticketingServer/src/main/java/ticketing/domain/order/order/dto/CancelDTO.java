package ticketing.domain.order.order.dto;

import lombok.Builder;
import lombok.Getter;

public class CancelDTO {

	@Getter
	@Builder
	public static class Prepared {
		String tid;
		int amount;
	}

	@Getter
	@Builder
	public static class Command {
		Long orderId;
		Long userId;

		public static Command of(Long orderId, Long userId) {
			return Command.builder()
				.orderId(orderId)
				.userId(userId)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Result {
		Long orderId;

		public Response toResponse() {
			return Response.builder()
				.orderId(orderId)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		Long orderId;
	}
}
