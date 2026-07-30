package ticketing.domain.concert.scheduleseat.repository;

import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;

public interface ScheduleSeatGradeProjection {
	Long getScheduleSeatId();
	ScheduleSeat.SeatStatus getSeatStatus();
	Long getSeatGradeId();
	String getSeatGradeName();
}
