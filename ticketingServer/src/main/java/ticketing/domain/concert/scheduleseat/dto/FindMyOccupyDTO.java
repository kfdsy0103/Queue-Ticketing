package ticketing.domain.concert.scheduleseat.dto;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;

public class FindMyOccupyDTO {

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

		public static Item of(ScheduleSeat scheduleSeat, int price, LocalDateTime expiresAt) {
			return Item.builder()
				.concertScheduleId(scheduleSeat.getConcertSchedule().getId())
				.scheduleSeatId(scheduleSeat.getId())
				.seatId(scheduleSeat.getSeat().getId())
				.seatNumber(scheduleSeat.getSeat().getSeatNumber())
				.seatGradeName(scheduleSeat.getSeat().getSeatGrade().getName())
				.price(price)
				.expiresAt(expiresAt)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Result {
		List<Item> seats;

		public static Result of(List<Item> seats) {
			return Result.builder()
				.seats(seats)
				.build();
		}

		public static Result empty() {
			return Result.of(Collections.emptyList());
		}

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

	@Getter
	@EqualsAndHashCode
	@AllArgsConstructor
	public static class SchedulePriceKey {
		Long concertScheduleId;
		Long seatGradeId;

		public static SchedulePriceKey of(Long concertScheduleId, Long seatGradeId) {
			return new SchedulePriceKey(concertScheduleId, seatGradeId);
		}
	}
}
