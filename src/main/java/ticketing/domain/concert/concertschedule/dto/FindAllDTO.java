package ticketing.domain.concert.concertschedule.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.concert.concertschedule.entity.ConcertSchedule;

public class FindAllDTO {

	@Getter
	@Builder
	public static class Command {
		Long concertId;

		public static Command of(Long concertId) {
			return Command.builder()
				.concertId(concertId)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Result {
		List<ConcertScheduleInfo> concertSchedules;

		public static Result of(List<ConcertScheduleInfo> concertSchedules) {
			return Result.builder()
				.concertSchedules(concertSchedules)
				.build();
		}

		public Response toResponse() {
			return Response.builder()
				.concertSchedules(concertSchedules)
				.build();
		}
	}

	@Getter
	@Builder
	public static class ConcertScheduleInfo {
		Long concertScheduleId;
		Long concertId;
		LocalDate performanceDate;
		LocalDateTime ticketOpenAt;

		public static ConcertScheduleInfo from(ConcertSchedule concertSchedule) {
			return ConcertScheduleInfo.builder()
				.concertScheduleId(concertSchedule.getId())
				.concertId(concertSchedule.getConcert().getId())
				.performanceDate(concertSchedule.getPerformanceDate())
				.ticketOpenAt(concertSchedule.getTicketOpenAt())
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		List<ConcertScheduleInfo> concertSchedules;
	}
}
