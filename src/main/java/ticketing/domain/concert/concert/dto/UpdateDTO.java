package ticketing.domain.concert.concert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.domain.concert.concert.entity.Concert;

public class UpdateDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		@NotNull
		Long venueId;
		@NotBlank
		String title;
		String content;

		public Command toCommand() {
			return Command.builder()
				.venueId(venueId)
				.title(title)
				.content(content)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Command {
		Long venueId;
		String title;
		String content;
	}

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
