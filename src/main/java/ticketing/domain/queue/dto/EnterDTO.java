package ticketing.domain.queue.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.domain.queue.enums.EnterType;

public class EnterDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		Long userId;
		Long concertScheduleId;
		EnterType enterType;

		public Command toCommand() {
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
		boolean needToChoose;
		long rank;
		long pollingIntervalMs;

		public Response toResponse() {
			return Response.builder()
				.token(token)
				.needToChoose(needToChoose)
				.rank(rank)
				.pollingIntervalMs(pollingIntervalMs)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		String token;
		boolean needToChoose;
		long rank;
		long pollingIntervalMs;
	}
}
