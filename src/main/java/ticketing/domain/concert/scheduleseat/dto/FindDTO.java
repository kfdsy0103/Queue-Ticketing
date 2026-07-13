package ticketing.domain.concert.scheduleseat.dto;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;

public class FindDTO {

	@Getter
	@Builder
	public static class Command {
		Long scheduleSeatId;
		Long userId;
	}

	@Getter
	@Builder
	public static class Result {
		Long scheduleSeatId;
		Long concertScheduleId;
		Long seatId;
		ScheduleSeat.SeatStatus seatStatus;
		boolean occupiedByMe;

		public Response toResponse() {
			return Response.builder()
				.scheduleSeatId(scheduleSeatId)
				.concertScheduleId(concertScheduleId)
				.seatId(seatId)
				.seatStatus(seatStatus)
				.occupiedByMe(occupiedByMe)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		Long scheduleSeatId;
		Long concertScheduleId;
		Long seatId;
		ScheduleSeat.SeatStatus seatStatus;
		boolean occupiedByMe;
	}
}
