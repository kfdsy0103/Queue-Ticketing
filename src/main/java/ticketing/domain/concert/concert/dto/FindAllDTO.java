package ticketing.domain.concert.concert.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.concert.concert.entity.Concert;

public class FindAllDTO {

	@Getter
	@Builder
	public static class Result {
		List<ConcertInfo> concerts;

		public static Result of(List<ConcertInfo> concerts) {
			return Result.builder()
				.concerts(concerts)
				.build();
		}

		public Response toResponse() {
			return Response.builder()
				.concerts(concerts)
				.build();
		}
	}

	@Getter
	@Builder
	public static class ConcertInfo {
		Long concertId;
		String title;
		String content;
		Long venueId;

		public static ConcertInfo from(Concert concert) {
			return ConcertInfo.builder()
				.concertId(concert.getId())
				.title(concert.getTitle())
				.content(concert.getContent())
				.venueId(concert.getVenue().getId())
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		List<ConcertInfo> concerts;
	}
}
