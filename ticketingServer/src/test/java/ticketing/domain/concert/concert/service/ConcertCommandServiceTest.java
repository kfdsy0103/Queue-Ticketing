package ticketing.domain.concert.concert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ticketing.domain.concert.concert.dto.CreateDTO;
import ticketing.domain.concert.concert.repository.ConcertRepository;
import ticketing.domain.concert.concertschedule.repository.ConcertScheduleRepository;
import ticketing.domain.concert.scheduleprice.repository.SchedulePriceRepository;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.concert.scheduleseat.repository.ScheduleSeatRepository;
import ticketing.domain.seatgrade.entity.SeatGrade;
import ticketing.domain.seatgrade.exception.SeatGradeErrorCode;
import ticketing.domain.seatgrade.repository.SeatGradeRepository;
import ticketing.domain.venue.seat.repository.SeatRepository;
import ticketing.domain.venue.venue.entity.Venue;
import ticketing.domain.venue.venue.repository.VenueRepository;
import ticketing.fixture.VenueFixture;
import ticketing.global.apiPayload.exception.GeneralException;

/**
 * ConcertCommandService가 회차마다 공연장 전 좌석을 AVAILABLE 좌석으로 복제하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ConcertCommandServiceTest {

	@InjectMocks
	private ConcertCommandService concertCommandService;

	@Mock
	private ConcertRepository concertRepository;

	@Mock
	private ConcertScheduleRepository concertScheduleRepository;

	@Mock
	private SchedulePriceRepository schedulePriceRepository;

	@Mock
	private ScheduleSeatRepository scheduleSeatRepository;

	@Mock
	private VenueRepository venueRepository;

	@Mock
	private SeatGradeRepository seatGradeRepository;

	@Mock
	private SeatRepository seatRepository;

	@Captor
	private ArgumentCaptor<List<ScheduleSeat>> scheduleSeatsCaptor;

	private static CreateDTO.Command createCommand(int scheduleCount) {
		List<CreateDTO.Command.ScheduleCommand> schedules = IntStream.range(0, scheduleCount)
			.mapToObj(index -> CreateDTO.Command.ScheduleCommand.builder()
				.performanceDate(LocalDate.of(2026, 12, 25).plusDays(index))
				.ticketOpenAt(LocalDateTime.of(2026, 11, 1, 20, 0))
				.prices(List.of(CreateDTO.Command.PriceCommand.builder()
					.seatGradeId(1L)
					.price(50_000)
					.build()))
				.build())
			.toList();

		return CreateDTO.Command.builder()
			.title("테스트 콘서트")
			.content("설명")
			.venueId(1L)
			.schedules(schedules)
			.build();
	}

	@Test
	void 회차마다_공연장의_모든_좌석이_AVAILABLE로_등록된다() {
		// given
		Venue venue = VenueFixture.venue(1L);
		SeatGrade seatGrade = VenueFixture.seatGrade(1L, "VIP");
		given(venueRepository.findById(1L)).willReturn(Optional.of(venue));
		given(seatRepository.findAllByVenueId(1L)).willReturn(List.of(
			VenueFixture.seat(100L, venue, seatGrade, "A1"),
			VenueFixture.seat(101L, venue, seatGrade, "A2"),
			VenueFixture.seat(102L, venue, seatGrade, "A3")
		));
		given(seatGradeRepository.findById(1L)).willReturn(Optional.of(seatGrade));

		// when
		concertCommandService.create(createCommand(2));

		// then
		verify(concertScheduleRepository, times(2)).save(any());
		verify(scheduleSeatRepository, times(2)).saveAll(scheduleSeatsCaptor.capture());
		assertThat(scheduleSeatsCaptor.getAllValues()).hasSize(2);
		assertThat(scheduleSeatsCaptor.getAllValues()).allSatisfy(scheduleSeats -> {
			assertThat(scheduleSeats).hasSize(3);
			assertThat(scheduleSeats).allSatisfy(scheduleSeat ->
				assertThat(scheduleSeat.getSeatStatus()).isEqualTo(ScheduleSeat.SeatStatus.AVAILABLE));
		});
	}

	@Test
	void 존재하지_않는_좌석_등급으로_가격을_책정하면_SEAT_GRADE_NOT_FOUND_예외가_발생한다() {
		// given
		given(venueRepository.findById(1L)).willReturn(Optional.of(VenueFixture.venue(1L)));
		given(seatRepository.findAllByVenueId(1L)).willReturn(List.of());
		given(seatGradeRepository.findById(1L)).willReturn(Optional.empty());

		// when
		Throwable thrown = catchThrowable(() -> concertCommandService.create(createCommand(1)));

		// then
		assertThat(thrown).isInstanceOf(GeneralException.class);
		assertThat(((GeneralException)thrown).getCode()).isEqualTo(SeatGradeErrorCode.SEAT_GRADE_NOT_FOUND);
	}
}
