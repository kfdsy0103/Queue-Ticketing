package ticketing.domain.concert.concert.dto;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.concert.concert.entity.Concert;

public class FindDTO {

	@Getter
	@Builder
	public static class Command {
		Long concertId;
	}

	@Getter
	@Builder
	public static class Result {
		Long concertId;
		String title;
		String content;
		Long venueId;

		public static Result from(Concert concert) {
			return Result.builder()
				.concertId(concert.getId())
				.title(concert.getTitle())
				.content(concert.getContent())
				.venueId(concert.getVenue().getId())
				.build();
		}

		public Response toResponse() {
			return Response.builder()
				.concertId(concertId)
				.title(title)
				.content(content)
				.venueId(venueId)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		Long concertId;
		String title;
		String content;
		Long venueId;
	}
}
