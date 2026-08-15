package ticketing.domain.seatgrade.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.seatgrade.entity.SeatGrade;

public class FindAllDTO {

	@Getter
	@Builder
	public static class Result {
		List<SeatGradeInfo> seatGrades;

		@Getter
		@Builder
		public static class SeatGradeInfo {
			Long seatGradeId;
			String name;

			public static SeatGradeInfo from(SeatGrade seatGrade) {
				return SeatGradeInfo.builder()
					.seatGradeId(seatGrade.getId())
					.name(seatGrade.getName())
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
		List<Result.SeatGradeInfo> seatGrades;
	}
}
