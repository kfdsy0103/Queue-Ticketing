package ticketing.domain.venue.venue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.domain.venue.venue.entity.Venue;

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

		public static Response from(Venue venue) {
			return Response.builder()
				.id(venue.getId())
				.name(venue.getName())
				.build();
		}
	}
}
