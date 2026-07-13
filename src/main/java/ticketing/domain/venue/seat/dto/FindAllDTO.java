package ticketing.domain.venue.seat.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.venue.seat.entity.Seat;

public class FindAllDTO {

	@Getter
	@Builder
	public static class Result {
		List<SeatInfo> seatInfos;

		@Getter
		@Builder
		public static class SeatInfo {
			Long id;
			Long venueId;
			Long seatGradeId;
			String seatNumber;

			public static SeatInfo from(Seat seat) {
				return SeatInfo.builder()
					.id(seat.getId())
					.venueId(seat.getVenue().getId())
					.seatGradeId(seat.getSeatGrade().getId())
					.seatNumber(seat.getSeatNumber())
					.build();
			}
		}

		public Response toResponse() {
			return Response.builder()
				.seatInfos(seatInfos)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		List<Result.SeatInfo> seatInfos;
	}
}
