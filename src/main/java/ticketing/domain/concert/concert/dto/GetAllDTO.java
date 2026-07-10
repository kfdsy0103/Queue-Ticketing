package ticketing.domain.concert.concert.dto;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.concert.concert.entity.Concert;

public class GetAllDTO {

	@Getter
	@Builder
	public static class Response {
		Long id;
		Long venueId;
		String title;
		String content;

		public static Response from(Concert concert) {
			return Response.builder()
				.id(concert.getId())
				.venueId(concert.getVenue().getId())
				.title(concert.getTitle())
				.content(concert.getContent())
				.build();
		}
	}
}
