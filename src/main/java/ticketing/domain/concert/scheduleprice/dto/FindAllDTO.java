package ticketing.domain.concert.scheduleprice.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import ticketing.domain.concert.scheduleprice.entity.SchedulePrice;

public class FindAllDTO {

	@Getter
	@Builder
	public static class Result {
		List<SchedulePriceInfo> schedulePrices;

		@Getter
		@Builder
		public static class SchedulePriceInfo {
			Long schedulePriceId;
			Long concertScheduleId;
			Long seatGradeId;
			int price;

			public static SchedulePriceInfo from(SchedulePrice schedulePrice) {
				return SchedulePriceInfo.builder()
					.schedulePriceId(schedulePrice.getId())
					.concertScheduleId(schedulePrice.getConcertSchedule().getId())
					.seatGradeId(schedulePrice.getSeatGrade().getId())
					.price(schedulePrice.getPrice())
					.build();
			}
		}

		public Response toResponse() {
			return Response.builder()
				.schedulePrices(schedulePrices)
				.build();
		}
	}

	@Getter
	@Builder
	public static class Response {
		List<Result.SchedulePriceInfo> schedulePrices;
	}
}
