package ticketing.domain.queue.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class StatusDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		Long userId;
		String token;

		public StatusDTO.Command toCommand() {
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
		long pollingIntervalMs;
		// TODO: 좌석 전체 점유율

		public StatusDTO.Response toResponse() {
			return Response.builder()
				.rank(rank)
				.pollingIntervalMs(pollingIntervalMs)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		long rank;
		long pollingIntervalMs;
		// TODO: 좌석 전체 점유율
	}
}
