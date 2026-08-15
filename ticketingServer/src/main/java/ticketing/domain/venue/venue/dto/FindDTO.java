package ticketing.domain.venue.venue.dto;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.venue.venue.entity.Venue;

public class FindDTO {

	@Getter
	@Builder
	public static class Command {
		Long venueId;

		public static Command of(Long venueId) {
			return Command.builder()
				.venueId(venueId)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Result {
		Long venueId;
		String name;

		public static Result from(Venue venue) {
			return Result.builder()
				.venueId(venue.getId())
				.name(venue.getName())
				.build();
		}

		public Response toResponse() {
			return Response.builder()
				.venueId(venueId)
				.name(name)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		Long venueId;
		String name;
	}
}
