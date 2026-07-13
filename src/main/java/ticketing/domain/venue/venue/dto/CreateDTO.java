package ticketing.domain.venue.venue.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.domain.venue.seat.entity.Seat;
import ticketing.domain.venue.venue.entity.Venue;

public class CreateDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		@NotBlank
		String name;
		@NotEmpty
		@Valid
		List<SeatRequest> seats;

		public Command toCommand() {
			return Command.builder()
				.name(name)
				.seats(seats.stream().map(SeatRequest::toCommand).toList())
				.build();
		}

		@Getter
		@NoArgsConstructor
		public static class SeatRequest {
			@NotNull
			Long seatGradeId;
			@NotBlank
			String seatNumber;

			public Command.SeatCommand toCommand() {
				return Command.SeatCommand.builder()
					.seatGradeId(seatGradeId)
					.seatNumber(seatNumber)
					.build();
			}
		}
	}

	@Getter
	@Builder
	public static class Command {
		String name;
		List<SeatCommand> seats;

		@Getter
		@Builder
		public static class SeatCommand {
			Long seatGradeId;
			String seatNumber;
		}
	}

	@Getter
	@Builder
	public static class Result {
		Long id;
		String name;
		List<SeatInfo> seats;

		@Getter
		@Builder
		public static class SeatInfo {
			Long id;
			Long seatGradeId;
			String seatNumber;

			public static SeatInfo from(Seat seat) {
				return SeatInfo.builder()
					.id(seat.getId())
					.seatGradeId(seat.getSeatGrade().getId())
					.seatNumber(seat.getSeatNumber())
					.build();
			}
		}

		public static Result from(Venue venue, List<Seat> seats) {
			return Result.builder()
				.id(venue.getId())
				.name(venue.getName())
				.seats(seats.stream().map(SeatInfo::from).toList())
				.build();
		}

		public Response toResponse() {
			return Response.builder()
				.id(id)
				.name(name)
				.seats(seats)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		Long id;
		String name;
		List<Result.SeatInfo> seats;
	}
}
