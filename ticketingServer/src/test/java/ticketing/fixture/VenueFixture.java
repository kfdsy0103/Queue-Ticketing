package ticketing.fixture;

import ticketing.domain.seatgrade.entity.SeatGrade;
import ticketing.domain.venue.seat.entity.Seat;
import ticketing.domain.venue.venue.entity.Venue;

public final class VenueFixture {

	private VenueFixture() {
	}

	public static Venue venue(Long id) {
		return Venue.builder()
			.id(id)
			.name("올림픽공원 체조경기장")
			.build();
	}

	public static SeatGrade seatGrade(Long id, String name) {
		return SeatGrade.builder()
			.id(id)
			.name(name)
			.build();
	}

	public static Seat seat(Long id, Venue venue, SeatGrade seatGrade, String seatNumber) {
		return Seat.builder()
			.id(id)
			.venue(venue)
			.seatGrade(seatGrade)
			.seatNumber(seatNumber)
			.build();
	}
}
