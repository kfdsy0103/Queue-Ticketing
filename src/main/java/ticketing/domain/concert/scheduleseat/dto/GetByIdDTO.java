package ticketing.domain.concert.scheduleseat.dto;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;

public class GetByIdDTO {

	@Getter
	@Builder
	public static class Response {
		Long id;
		Long concertScheduleId;
		Long seatId;
		ScheduleSeat.SeatStatus seatStatus;

		public static Response from(ScheduleSeat scheduleSeat) {
			return Response.builder()
				.id(scheduleSeat.getId())
				.concertScheduleId(scheduleSeat.getConcertSchedule().getId())
				.seatId(scheduleSeat.getSeat().getId())
				.seatStatus(scheduleSeat.getSeatStatus())
				.build();
		}
	}
}
