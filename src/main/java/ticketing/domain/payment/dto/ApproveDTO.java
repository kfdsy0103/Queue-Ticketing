package ticketing.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class ApproveDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		@NotNull
		Long userId;
		@NotNull
		Long orderId;
		@NotBlank
		String pgToken;

		public Command toCommand() {
			return Command.builder()
				.userId(userId)
				.orderId(orderId)
				.pgToken(pgToken)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Command {
		Long userId;
		Long orderId;
		String pgToken;
	}

	@Getter
	@Builder
	public static class Result {
		Long orderId;
		String tid;

		public Response toResponse() {
			return Response.builder()
				.orderId(orderId)
				.tid(tid)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		Long orderId;
		String tid;
	}
}
