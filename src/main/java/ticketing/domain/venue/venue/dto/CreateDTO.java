package ticketing.domain.venue.venue.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.domain.venue.seat.entity.Seat;
import ticketing.domain.venue.seatgrade.entity.SeatGrade;
import ticketing.domain.venue.venue.entity.Venue;

public class CreateDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		@NotBlank
		String name;
		@NotEmpty
		@Valid
		List<SeatGradeRequest> seatGrades;
		@NotEmpty
		@Valid
		List<SeatRequest> seats;

		public Command toCommand() {
			return Command.builder()
				.name(name)
				.seatGrades(seatGrades.stream().map(SeatGradeRequest::toCommand).toList())
				.seats(seats.stream().map(SeatRequest::toCommand).toList())
				.build();
		}

		@Getter
		@NoArgsConstructor
		public static class SeatGradeRequest {
			@NotBlank
			String name;

			public Command.SeatGradeCommand toCommand() {
				return Command.SeatGradeCommand.builder()
					.name(name)
					.build();
			}
		}

		@Getter
		@NoArgsConstructor
		public static class SeatRequest {
			@NotBlank
			String seatGradeName;
			@NotBlank
			String seatNumber;

			public Command.SeatCommand toCommand() {
				return Command.SeatCommand.builder()
					.seatGradeName(seatGradeName)
					.seatNumber(seatNumber)
					.build();
			}
		}
	}

	@Getter
	@Builder
	public static class Command {
		String name;
		List<SeatGradeCommand> seatGrades;
		List<SeatCommand> seats;

		@Getter
		@Builder
		public static class SeatGradeCommand {
			String name;
		}

		@Getter
		@Builder
		public static class SeatCommand {
			String seatGradeName;
			String seatNumber;
		}
	}

	@Getter
	@Builder
	public static class Result {
		Long venueId;
		String name;
		List<SeatGradeInfo> seatGrades;
		List<SeatInfo> seats;

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

		@Getter
		@Builder
		public static class SeatInfo {
			Long seatId;
			Long seatGradeId;
			String seatNumber;

			public static SeatInfo from(Seat seat) {
				return SeatInfo.builder()
					.seatId(seat.getId())
					.seatGradeId(seat.getSeatGrade().getId())
					.seatNumber(seat.getSeatNumber())
					.build();
			}
		}

		public static Result from(Venue venue, List<SeatGrade> seatGrades, List<Seat> seats) {
			return Result.builder()
				.venueId(venue.getId())
				.name(venue.getName())
				.seatGrades(seatGrades.stream().map(SeatGradeInfo::from).toList())
				.seats(seats.stream().map(SeatInfo::from).toList())
				.build();
		}

		public Response toResponse() {
			return Response.builder()
				.venueId(venueId)
				.name(name)
				.seatGrades(seatGrades)
				.seats(seats)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		Long venueId;
		String name;
		List<Result.SeatGradeInfo> seatGrades;
		List<Result.SeatInfo> seats;
	}
}
