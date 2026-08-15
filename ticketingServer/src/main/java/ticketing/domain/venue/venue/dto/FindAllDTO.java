package ticketing.domain.venue.venue.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.venue.venue.entity.Venue;

public class FindAllDTO {

	@Getter
	@Builder
	public static class Result {
		List<VenueInfo> venues;

		@Getter
		@Builder
		public static class VenueInfo {
			Long venueId;
			String name;

			public static VenueInfo from(Venue venue) {
				return VenueInfo.builder()
					.venueId(venue.getId())
					.name(venue.getName())
					.build();
			}
		}

		public Response toResponse() {
			return Response.builder()
				.venues(venues)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		List<Result.VenueInfo> venues;
	}
}
