package ticketing.domain.concert.scheduleseat.dto;

import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.concert.scheduleseat.enums.SeatDisplayStatus;

public class FindAllDTO {

	@Getter
	@Builder
	public static class Command {
		Long userId;
		Long concertScheduleId;
		String token;

		public static Command of(Long userId, Long concertScheduleId, String token) {
			return Command.builder()
				.userId(userId)
				.concertScheduleId(concertScheduleId)
				.token(token)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Result {
		List<ScheduleSeatInfo> scheduleSeats;

		public static Result of(List<ScheduleSeatInfo> scheduleSeats) {
			return Result.builder()
				.scheduleSeats(scheduleSeats)
				.build();
		}

		public static Result empty() {
			return Result.of(Collections.emptyList());
		}

		@Getter
		@Builder
		public static class ScheduleSeatInfo {
			Long scheduleSeatId;
			Long concertScheduleId;
			Long seatId;
			SeatDisplayStatus seatStatus;

			public static ScheduleSeatInfo of(ScheduleSeat scheduleSeat, SeatDisplayStatus seatStatus) {
				return ScheduleSeatInfo.builder()
					.scheduleSeatId(scheduleSeat.getId())
					.concertScheduleId(scheduleSeat.getConcertSchedule().getId())
					.seatId(scheduleSeat.getSeat().getId())
					.seatStatus(seatStatus)
					.build();
			}
		}

		public Response toResponse() {
			return Response.builder()
				.scheduleSeats(scheduleSeats)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		List<Result.ScheduleSeatInfo> scheduleSeats;
	}
}
