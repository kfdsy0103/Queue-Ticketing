package ticketing.fixture;

import ticketing.domain.concert.concertschedule.entity.ConcertSchedule;
import ticketing.domain.concert.scheduleseat.dto.ScheduleSeatLayoutDTO;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.concert.scheduleseat.projection.ScheduleSeatGradeProjection;
import ticketing.domain.concert.scheduleseat.projection.ScheduleSeatStatusProjection;
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

	/**
	 * 회차·좌석 그래프까지 기본값으로 채운 단축 팩토리. 좌석 자체가 관심사가 아닐 때 쓴다.
	 */
	public static ScheduleSeat availableScheduleSeat(Long id) {
		return availableScheduleSeat(id, defaultConcertSchedule(), defaultSeat(id));
	}

	public static ScheduleSeat soldScheduleSeat(Long id) {
		return soldScheduleSeat(id, defaultConcertSchedule(), defaultSeat(id));
	}

	public static ConcertSchedule defaultConcertSchedule() {
		return ConcertFixture.concertSchedule(1L, ConcertFixture.concert(1L, VenueFixture.venue(1L)));
	}

	public static Seat defaultSeat(Long seatId) {
		return VenueFixture.seat(seatId, VenueFixture.venue(1L), VenueFixture.seatGrade(1L, "VIP"), "A" + seatId);
	}

	public static ScheduleSeatLayoutDTO.Item layoutItem(Long scheduleSeatId, Long seatId, ScheduleSeat.SeatStatus seatStatus) {
		return ScheduleSeatLayoutDTO.Item.builder()
			.scheduleSeatId(scheduleSeatId)
			.seatId(seatId)
			.seatStatus(seatStatus)
			.build();
	}

	public static ScheduleSeatStatusProjection statusProjection(Long scheduleSeatId, Long seatId, ScheduleSeat.SeatStatus seatStatus) {
		return new ScheduleSeatStatusProjection() {
			@Override
			public Long getScheduleSeatId() {
				return scheduleSeatId;
			}

			@Override
			public Long getSeatId() {
				return seatId;
			}

			@Override
			public ScheduleSeat.SeatStatus getSeatStatus() {
				return seatStatus;
			}
		};
	}

	public static ScheduleSeatGradeProjection gradeProjection(Long scheduleSeatId, Long seatGradeId, String seatGradeName) {
		return new ScheduleSeatGradeProjection() {
			@Override
			public Long getScheduleSeatId() {
				return scheduleSeatId;
			}

			@Override
			public Long getSeatGradeId() {
				return seatGradeId;
			}

			@Override
			public String getSeatGradeName() {
				return seatGradeName;
			}
		};
	}
}
