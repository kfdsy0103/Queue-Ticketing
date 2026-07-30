package ticketing.domain.concert.concertschedule.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
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
	public static class Result {
		Long concertScheduleId;
		Long concertId;
		LocalDate performanceDate;
		LocalDateTime ticketOpenAt;
		List<SeatGradeRemaining> seatGrades;

		public static Result from(ConcertSchedule concertSchedule, List<SeatGradeRemaining> seatGrades) {
			return Result.builder()
				.concertScheduleId(concertSchedule.getId())
				.concertId(concertSchedule.getConcert().getId())
				.performanceDate(concertSchedule.getPerformanceDate())
				.ticketOpenAt(concertSchedule.getTicketOpenAt())
				.seatGrades(seatGrades)
				.build();
		}

		public Response toResponse() {
			return Response.builder()
				.concertScheduleId(concertScheduleId)
				.concertId(concertId)
				.performanceDate(performanceDate)
				.ticketOpenAt(ticketOpenAt)
				.seatGrades(seatGrades)
				.build();
		}
	}

	@Getter
	@Builder
	public static class SeatGradeRemaining {
		Long seatGradeId;
		String seatGradeName;
		Long remainingSeatCount;

		public static SeatGradeRemaining of(Long seatGradeId, String seatGradeName, long remainingSeatCount) {
			return SeatGradeRemaining.builder()
				.seatGradeId(seatGradeId)
				.seatGradeName(seatGradeName)
				.remainingSeatCount(remainingSeatCount)
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
		List<SeatGradeRemaining> seatGrades;
	}
}
