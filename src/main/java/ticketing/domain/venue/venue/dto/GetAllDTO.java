package ticketing.domain.venue.venue.dto;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.venue.venue.entity.Venue;

public class GetAllDTO {

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
