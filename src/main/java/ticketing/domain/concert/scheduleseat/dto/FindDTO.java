package ticketing.domain.concert.scheduleseat.dto;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.concert.scheduleseat.enums.SeatDisplayStatus;

public class FindDTO {

	@Getter
	@Builder
	public static class Command {
		Long scheduleSeatId;
		Long userId;
		String token;

		public static Command of(Long scheduleSeatId, Long userId, String token) {
			return Command.builder()
				.scheduleSeatId(scheduleSeatId)
				.userId(userId)
				.token(token)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Result {
		Long scheduleSeatId;
		Long concertScheduleId;
		Long seatId;
		SeatDisplayStatus seatStatus;

		public Response toResponse() {
			return Response.builder()
				.scheduleSeatId(scheduleSeatId)
				.concertScheduleId(concertScheduleId)
				.seatId(seatId)
				.seatStatus(seatStatus)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		Long scheduleSeatId;
		Long concertScheduleId;
		Long seatId;
		SeatDisplayStatus seatStatus;
	}
}
