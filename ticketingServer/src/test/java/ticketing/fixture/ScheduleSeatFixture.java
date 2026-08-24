package ticketing.fixture;

import ticketing.domain.concert.concertschedule.entity.ConcertSchedule;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.venue.seat.entity.Seat;

public final class ScheduleSeatFixture {

	private ScheduleSeatFixture() {
	}

	public static ScheduleSeat availableScheduleSeat(Long id, ConcertSchedule concertSchedule, Seat seat) {
		return ScheduleSeat.builder()
			.id(id)
			.concertSchedule(concertSchedule)
			.seat(seat)
			.seatStatus(ScheduleSeat.SeatStatus.AVAILABLE)
			.build();
	}

	public static ScheduleSeat soldScheduleSeat(Long id, ConcertSchedule concertSchedule, Seat seat) {
		return ScheduleSeat.builder()
			.id(id)
			.concertSchedule(concertSchedule)
			.seat(seat)
			.seatStatus(ScheduleSeat.SeatStatus.SOLD)
			.build();
	}
}
