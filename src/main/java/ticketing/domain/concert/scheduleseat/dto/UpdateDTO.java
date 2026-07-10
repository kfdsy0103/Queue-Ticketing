package ticketing.domain.concert.scheduleseat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;

public class UpdateDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		@NotNull
		Long concertScheduleId;
		@NotNull
		Long seatId;
		@NotNull
		ScheduleSeat.SeatStatus seatStatus;

		public Command toCommand() {
			return Command.builder()
				.concertScheduleId(concertScheduleId)
				.seatId(seatId)
				.seatStatus(seatStatus)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Command {
		Long concertScheduleId;
		Long seatId;
		ScheduleSeat.SeatStatus seatStatus;
	}

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
