package ticketing.domain.venue.seatgrade.dto;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.venue.seatgrade.entity.SeatGrade;

public class GetByIdDTO {

	@Getter
	@Builder
	public static class Response {
		Long id;
		String name;

		public static Response from(SeatGrade seatGrade) {
			return Response.builder()
				.id(seatGrade.getId())
				.name(seatGrade.getName())
				.build();
		}
	}
}
