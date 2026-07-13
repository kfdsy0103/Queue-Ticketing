package ticketing.domain.concert.concertschedule.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.concert.concertschedule.entity.ConcertSchedule;

public class FindDTO {

	@Getter
	@Builder
	public static class Command {
		Long concertScheduleId;
	}

	@Getter
	@Builder
	public static class Result {
		Long concertScheduleId;
		Long concertId;
		Long venueId;
		LocalDate performanceDate;
		LocalDateTime ticketOpenAt;

		public static Result from(ConcertSchedule concertSchedule) {
			return Result.builder()
				.concertScheduleId(concertSchedule.getId())
				.concertId(concertSchedule.getConcert().getId())
				.venueId(concertSchedule.getVenue().getId())
				.performanceDate(concertSchedule.getPerformanceDate())
				.ticketOpenAt(concertSchedule.getTicketOpenAt())
				.build();
		}

		public Response toResponse() {
			return Response.builder()
				.concertScheduleId(concertScheduleId)
				.concertId(concertId)
				.venueId(venueId)
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
		Long venueId;
		LocalDate performanceDate;
		LocalDateTime ticketOpenAt;
	}
}
