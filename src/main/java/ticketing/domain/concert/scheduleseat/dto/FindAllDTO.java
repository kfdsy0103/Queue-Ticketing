package ticketing.domain.concert.scheduleseat.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

public class FindAllDTO {

	@Getter
	@Builder
	public static class Command {
		Long userId;
	}

	@Getter
	@Builder
	public static class Result {
		List<FindDTO.Result> scheduleSeats;

		public Response toResponse() {
			return Response.builder()
				.scheduleSeats(scheduleSeats.stream().map(FindDTO.Result::toResponse).toList())
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		List<FindDTO.Response> scheduleSeats;
	}
}
