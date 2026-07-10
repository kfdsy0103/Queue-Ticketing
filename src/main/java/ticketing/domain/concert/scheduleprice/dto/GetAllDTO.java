package ticketing.domain.concert.scheduleprice.dto;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.concert.scheduleprice.entity.SchedulePrice;

public class GetAllDTO {

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
