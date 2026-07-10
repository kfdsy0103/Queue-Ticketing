package ticketing.domain.concert.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.domain.concert.entity.ConcertSchedule;

public class ConcertScheduleDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		@NotNull
		Long concertId;
		@NotNull
		LocalDate performanceDate;
		@NotNull
		LocalDateTime ticketOpenAt;

		public Command toCommand() {
			return Command.builder()
				.concertId(concertId)
				.performanceDate(performanceDate)
				.ticketOpenAt(ticketOpenAt)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Command {
		Long concertId;
		LocalDate performanceDate;
		LocalDateTime ticketOpenAt;
	}

	@Getter
	@Builder
	public static class Response {
		Long id;
		Long concertId;
		LocalDate performanceDate;
		LocalDateTime ticketOpenAt;

		public static Response from(ConcertSchedule concertSchedule) {
			return Response.builder()
				.id(concertSchedule.getId())
				.concertId(concertSchedule.getConcert().getId())
				.performanceDate(concertSchedule.getPerformanceDate())
				.ticketOpenAt(concertSchedule.getTicketOpenAt())
				.build();
		}
	}
}
