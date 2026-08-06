package ticketing.domain.concert.scheduleseat.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

public class FindOccupyDTO {

	@Getter
	@Builder
	public static class Command {
		Long userId;
		Long concertScheduleId;

		public static Command of(Long userId, Long concertScheduleId) {
			return Command.builder()
				.userId(userId)
				.concertScheduleId(concertScheduleId)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Item {
		Long scheduleSeatId;
		Long seatId;
		String seatNumber;
		String seatGradeName;
		int price;
		LocalDateTime expiresAt;
	}

	@Getter
	@Builder
	public static class Result {
		List<Item> seats;

		public Response toResponse() {
			return Response.builder()
				.seats(seats)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		List<Item> seats;
	}
}
