package queue.domain.queue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class TakeoverDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		@NotNull
		Long concertScheduleId;

		public Command toCommand(Long userId) {
			return Command.builder()
				.userId(userId)
				.concertScheduleId(concertScheduleId)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Command {
		Long userId;
		Long concertScheduleId;
	}

	@Getter
	@Builder
	public static class Result {
		String token;
		long rank;
		long retryAfterMs;
		boolean isActive;
		String redirectEndpoint;

		public Response toResponse() {
			return Response.builder()
				.token(token)
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
		String token;
		long rank;
		long retryAfterMs;
		@JsonProperty("isActive")
		boolean isActive;
		String redirectEndpoint;
	}
}
