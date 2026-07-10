package ticketing.domain.venue.seatgrade.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.domain.venue.seatgrade.entity.SeatGrade;

public class UpdateDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		@NotBlank
		String name;

		public Command toCommand() {
			return Command.builder()
				.name(name)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Command {
		String name;
	}

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
