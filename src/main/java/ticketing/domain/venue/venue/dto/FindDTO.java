package ticketing.domain.venue.venue.dto;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.venue.venue.entity.Venue;

public class FindDTO {

	@Getter
	@Builder
	public static class Command {
		Long venueId;
	}

	@Getter
	@Builder
	public static class Result {
		Long id;
		String name;

		public static Result from(Venue venue) {
			return Result.builder()
				.id(venue.getId())
				.name(venue.getName())
				.build();
		}

		public Response toResponse() {
			return Response.builder()
				.id(id)
				.name(name)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		Long id;
		String name;
	}
}
