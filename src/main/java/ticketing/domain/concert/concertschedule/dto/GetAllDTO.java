package ticketing.domain.concert.concertschedule.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.concert.concertschedule.entity.ConcertSchedule;

public class GetAllDTO {

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
