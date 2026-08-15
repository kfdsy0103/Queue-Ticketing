package ticketing.domain.concert.scheduleseat.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

public class FindRemainingDTO {

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
		List<SeatGradeRemaining> seatGrades;

		public static Result of(List<SeatGradeRemaining> seatGrades) {
			return Result.builder()
				.seatGrades(seatGrades)
				.build();
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

		public Response toResponse() {
			return Response.builder()
				.seatGrades(seatGrades)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		List<Result.SeatGradeRemaining> seatGrades;
	}
}
