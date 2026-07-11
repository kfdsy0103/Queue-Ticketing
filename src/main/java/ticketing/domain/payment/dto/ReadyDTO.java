package ticketing.domain.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class ReadyDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		@NotNull
		Long userId;
		@NotNull
		Long orderId;

		public ReadyDTO.Command toCommand() {
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
		String redirectUrl;

		public Response toResponse() {
			return Response.builder()
				.redirectUrl(redirectUrl)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		String redirectUrl;
	}
}
