package ticketing.domain.concert.scheduleseat.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

public class FindOccupyDTO {

	@Getter
	@Builder
	public static class Command {
		Long userId;

		public static Command of(Long userId) {
			return Command.builder()
				.userId(userId)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Item {
		Long concertScheduleId;
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

	/**
	 * 회차·등급 쌍으로 가격을 찾기 위한 복합 키입니다.
	 */
	@Getter
	@EqualsAndHashCode
	@AllArgsConstructor
	public static class SchedulePriceKey {
		Long concertScheduleId;
		Long seatGradeId;
	}
}
