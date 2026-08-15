package ticketing.domain.concert.scheduleseat.repository.projection;

import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;

public interface ScheduleSeatStatusProjection {
	Long getScheduleSeatId();
	Long getSeatId();
	ScheduleSeat.SeatStatus getSeatStatus();
}
