package ticketing.domain.concert.scheduleprice.dto;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.concert.scheduleprice.entity.SchedulePrice;

public class FindDTO {

	@Getter
	@Builder
	public static class Command {
		Long schedulePriceId;

		public static Command of(Long schedulePriceId) {
			return Command.builder()
				.schedulePriceId(schedulePriceId)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Result {
		Long schedulePriceId;
		Long concertScheduleId;
		Long seatGradeId;
		int price;

		public static Result from(SchedulePrice schedulePrice) {
			return Result.builder()
				.schedulePriceId(schedulePrice.getId())
				.concertScheduleId(schedulePrice.getConcertSchedule().getId())
				.seatGradeId(schedulePrice.getSeatGrade().getId())
				.price(schedulePrice.getPrice())
				.build();
		}

		public Response toResponse() {
			return Response.builder()
				.schedulePriceId(schedulePriceId)
				.concertScheduleId(concertScheduleId)
				.seatGradeId(seatGradeId)
				.price(price)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		Long schedulePriceId;
		Long concertScheduleId;
		Long seatGradeId;
		int price;
	}
}
