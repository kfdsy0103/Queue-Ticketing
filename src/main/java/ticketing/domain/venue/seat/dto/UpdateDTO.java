package ticketing.domain.venue.seat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.domain.venue.seat.entity.Seat;

public class UpdateDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		@NotNull
		Long venueId;
		@NotNull
		Long seatGradeId;
		@NotBlank
		String seatNumber;

		public Command toCommand() {
			return Command.builder()
				.venueId(venueId)
				.seatGradeId(seatGradeId)
				.seatNumber(seatNumber)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Command {
		Long venueId;
		Long seatGradeId;
		String seatNumber;
	}

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
