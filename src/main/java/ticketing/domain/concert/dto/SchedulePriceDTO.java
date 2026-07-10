package ticketing.domain.concert.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.domain.concert.entity.SchedulePrice;

public class SchedulePriceDTO {

	@Getter
	@NoArgsConstructor
	public static class Request {
		@NotNull
		Long concertScheduleId;
		@NotNull
		Long seatGradeId;
		@NotNull
		Integer price;

		public Command toCommand() {
			return Command.builder()
				.concertScheduleId(concertScheduleId)
				.seatGradeId(seatGradeId)
				.price(price)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Command {
		Long concertScheduleId;
		Long seatGradeId;
		Integer price;
	}

	@Getter
	@Builder
	public static class Response {
		Long id;
		Long concertScheduleId;
		Long seatGradeId;
		int price;

		public static Response from(SchedulePrice schedulePrice) {
			return Response.builder()
				.id(schedulePrice.getId())
				.concertScheduleId(schedulePrice.getConcertSchedule().getId())
				.seatGradeId(schedulePrice.getSeatGrade().getId())
				.price(schedulePrice.getPrice())
				.build();
		}
	}
}
