package ticketing.fixture;

import java.time.LocalDate;
import java.time.LocalDateTime;

import ticketing.domain.concert.concert.entity.Concert;
import ticketing.domain.concert.concertschedule.entity.ConcertSchedule;
import ticketing.domain.concert.scheduleprice.entity.SchedulePrice;
import ticketing.domain.seatgrade.entity.SeatGrade;
import ticketing.domain.venue.venue.entity.Venue;

public final class ConcertFixture {

	private ConcertFixture() {
	}

	public static Concert concert(Long id, Venue venue) {
		return Concert.builder()
			.id(id)
			.venue(venue)
			.title("테스트 콘서트")
			.content("테스트 콘서트 설명")
			.build();
	}

	public static ConcertSchedule concertSchedule(Long id, Concert concert) {
		return ConcertSchedule.builder()
			.id(id)
			.concert(concert)
			.performanceDate(LocalDate.of(2026, 12, 25))
			.ticketOpenAt(LocalDateTime.of(2026, 11, 1, 20, 0))
			.build();
	}

	public static SchedulePrice schedulePrice(Long id, ConcertSchedule concertSchedule, SeatGrade seatGrade, int price) {
		return SchedulePrice.builder()
			.id(id)
			.concertSchedule(concertSchedule)
			.seatGrade(seatGrade)
			.price(price)
			.build();
	}
}
