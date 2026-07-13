package ticketing.domain.venue.seatgrade.dto;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.venue.seatgrade.entity.SeatGrade;

public class FindDTO {

	@Getter
	@Builder
	public static class Command {
		Long seatGradeId;
	}

	@Getter
	@Builder
	public static class Result {
		Long seatGradeId;
		String name;

		public static Result from(SeatGrade seatGrade) {
			return Result.builder()
				.seatGradeId(seatGrade.getId())
				.name(seatGrade.getName())
				.build();
		}

		public Response toResponse() {
			return Response.builder()
				.seatGradeId(seatGradeId)
				.name(name)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		Long seatGradeId;
		String name;
	}
}
