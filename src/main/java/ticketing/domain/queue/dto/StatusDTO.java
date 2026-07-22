package ticketing.domain.queue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class StatusDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		@NotBlank
		String token;

		public StatusDTO.Command toCommand(Long userId) {
			return StatusDTO.Command.builder()
				.userId(userId)
				.token(token)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Command {
		Long userId;
		String token;
	}

	@Getter
	@Builder
	public static class Result {
		long rank;
		long retryAfterMs;
		boolean isActive;
		String redirectEndpoint;

		public StatusDTO.Response toResponse() {
			return Response.builder()
				.rank(rank)
				.retryAfterMs(retryAfterMs)
				.isActive(isActive)
				.redirectEndpoint(redirectEndpoint)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		long rank;
		long retryAfterMs;
		boolean isActive;
		String redirectEndpoint;
	}
}
