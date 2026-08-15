package queue.domain.queue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Getter;

public class StatusDTO {

	@Getter
	@Builder
	public static class Command {
		Long userId;
		String token;

		public static Command of(Long userId, String token) {
			return Command.builder()
				.userId(userId)
				.token(token)
				.build();
		}
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
		@JsonProperty("isActive")
		boolean isActive;
		String redirectEndpoint;
	}
}
