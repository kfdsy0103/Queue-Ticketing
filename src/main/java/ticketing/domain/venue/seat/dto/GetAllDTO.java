package ticketing.domain.venue.seat.dto;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.venue.seat.entity.Seat;

public class GetAllDTO {

	@Getter
	@Builder
	public static class Response {
		Long id;
		Long venueId;
		Long seatGradeId;
		String seatNumber;

		public static Response from(Seat seat) {
			return Response.builder()
				.id(seat.getId())
				.venueId(seat.getVenue().getId())
				.seatGradeId(seat.getSeatGrade().getId())
				.seatNumber(seat.getSeatNumber())
				.build();
		}
	}
}
