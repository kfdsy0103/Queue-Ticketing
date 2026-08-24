package ticketing.domain.concert.scheduleseat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ticketing.domain.concert.concertschedule.entity.ConcertSchedule;
import ticketing.domain.concert.scheduleprice.exception.SchedulePriceErrorCode;
import ticketing.domain.concert.scheduleprice.repository.SchedulePriceRepository;
import ticketing.domain.concert.scheduleseat.dto.FindMyOccupyDTO;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.concert.scheduleseat.repository.ScheduleSeatRepository;
import ticketing.fixture.ConcertFixture;
import ticketing.fixture.ScheduleSeatFixture;
import ticketing.global.apiPayload.exception.GeneralException;

/**
 * ScheduleSeatQueryService가 점유 좌석에 회차·등급별 가격을 매핑하여 반환하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleSeatQueryServiceTest {

	private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 8, 25, 12, 0);
	private static final int SEAT_PRICE = 50_000;

	@InjectMocks
	private ScheduleSeatQueryService scheduleSeatQueryService;

	@Mock
	private ScheduleSeatRepository scheduleSeatRepository;

	@Mock
	private SchedulePriceRepository schedulePriceRepository;

	@Test
	void 점유_좌석에_회차와_등급에_맞는_가격이_채워져_반환된다() {
		// given
		ScheduleSeat scheduleSeat = ScheduleSeatFixture.availableScheduleSeat(10L);
		ConcertSchedule concertSchedule = scheduleSeat.getConcertSchedule();
		given(scheduleSeatRepository.findAllByIdInWithScheduleAndSeatGrade(any()))
			.willReturn(List.of(scheduleSeat));
		given(schedulePriceRepository.findAllByConcertScheduleIdIn(any()))
			.willReturn(List.of(ConcertFixture.schedulePrice(
				1L, concertSchedule, scheduleSeat.getSeat().getSeatGrade(), SEAT_PRICE)));

		// when
		List<FindMyOccupyDTO.Item> items = scheduleSeatQueryService.findMyOccupySeats(Map.of(10L, EXPIRES_AT));

		// then
		assertThat(items).hasSize(1);
		assertThat(items.getFirst().getScheduleSeatId()).isEqualTo(10L);
		assertThat(items.getFirst().getPrice()).isEqualTo(SEAT_PRICE);
		assertThat(items.getFirst().getExpiresAt()).isEqualTo(EXPIRES_AT);
	}

	@Test
	void 회차와_등급에_맞는_가격이_없으면_SCHEDULE_PRICE_NOT_FOUND_예외가_발생한다() {
		// given
		given(scheduleSeatRepository.findAllByIdInWithScheduleAndSeatGrade(any()))
			.willReturn(List.of(ScheduleSeatFixture.availableScheduleSeat(10L)));
		given(schedulePriceRepository.findAllByConcertScheduleIdIn(any())).willReturn(List.of());

		// when
		Throwable thrown = catchThrowable(
			() -> scheduleSeatQueryService.findMyOccupySeats(Map.of(10L, EXPIRES_AT)));

		// then
		assertThat(thrown).isInstanceOf(GeneralException.class);
		assertThat(((GeneralException)thrown).getCode()).isEqualTo(SchedulePriceErrorCode.SCHEDULE_PRICE_NOT_FOUND);
	}
}
