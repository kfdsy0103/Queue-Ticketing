package ticketing.domain.concert.scheduleseat.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.concert.scheduleseat.projection.ScheduleSeatStatusProjection;

public class ScheduleSeatLayoutDTO {

	@Getter
	@Builder
	@Jacksonized
	public static class Item {
		Long scheduleSeatId;
		Long seatId;
		ScheduleSeat.SeatStatus seatStatus;

		public static Item from(ScheduleSeatStatusProjection projection) {
			return Item.builder()
				.scheduleSeatId(projection.getScheduleSeatId())
				.seatId(projection.getSeatId())
				.seatStatus(projection.getSeatStatus())
				.build();
		}
	}
}
