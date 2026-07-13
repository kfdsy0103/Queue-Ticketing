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
		Long id;
		String title;
		String content;

		public static Result from(Concert concert) {
			return Result.builder()
				.id(concert.getId())
				.title(concert.getTitle())
				.content(concert.getContent())
				.build();
		}

		public Response toResponse() {
			return Response.builder()
				.id(id)
				.title(title)
				.content(content)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		Long id;
		String title;
		String content;
	}
}
