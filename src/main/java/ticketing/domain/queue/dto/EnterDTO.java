package ticketing.domain.queue.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.domain.queue.enums.EnterType;

public class EnterDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		@NotNull
		Long concertScheduleId;
		@NotNull
		EnterType enterType;

		public Command toCommand(Long userId) {
			return Command.builder()
				.userId(userId)
				.concertScheduleId(concertScheduleId)
				.enterType(enterType)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Command {
		Long userId;
		Long concertScheduleId;
		EnterType enterType;
	}

	@Getter
	@Builder
	public static class Result {
		String token;
		long rank;
		long pollingIntervalMs;

		public Response toResponse() {
			return Response.builder()
				.token(token)
				.rank(rank)
				.pollingIntervalMs(pollingIntervalMs)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		String token;
		long rank;
		long pollingIntervalMs;
	}
}
