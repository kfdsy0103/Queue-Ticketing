package ticketing.domain.concert.concertschedule.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import ticketing.domain.concert.concertschedule.entity.ConcertSchedule;

public class FindDTO {

	@Getter
	@Builder
	public static class Command {
		Long concertScheduleId;

		public static Command of(Long concertScheduleId) {
			return Command.builder()
				.concertScheduleId(concertScheduleId)
				.build();
		}
	}

	@Getter
	@Builder
	@Jacksonized
	public static class Result {
		Long concertScheduleId;
		Long concertId;
		LocalDate performanceDate;
		LocalDateTime ticketOpenAt;

		public static Result from(ConcertSchedule concertSchedule) {
			return Result.builder()
				.concertScheduleId(concertSchedule.getId())
				.concertId(concertSchedule.getConcert().getId())
				.performanceDate(concertSchedule.getPerformanceDate())
				.ticketOpenAt(concertSchedule.getTicketOpenAt())
				.build();
		}

		public Response toResponse() {
			return Response.builder()
				.concertScheduleId(concertScheduleId)
				.concertId(concertId)
				.performanceDate(performanceDate)
				.ticketOpenAt(ticketOpenAt)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		Long concertScheduleId;
		Long concertId;
		LocalDate performanceDate;
		LocalDateTime ticketOpenAt;
	}
}
