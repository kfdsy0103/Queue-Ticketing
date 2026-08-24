package ticketing.domain.concert.scheduleseat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ticketing.domain.concert.scheduleseat.exception.ScheduleSeatErrorCode;
import ticketing.domain.concert.scheduleseat.repository.ScheduleSeatRepository;
import ticketing.fixture.ScheduleSeatFixture;
import ticketing.global.apiPayload.exception.GeneralException;

/**
 * ScheduleSeatCommandService가 Master DB에서 점유 대상 좌석의 존재와 판매 여부를 검증하는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleSeatCommandServiceTest {

	@InjectMocks
	private ScheduleSeatCommandService scheduleSeatCommandService;

	@Mock
	private ScheduleSeatRepository scheduleSeatRepository;

	@Test
	void 조회된_좌석_수가_요청_수와_다르면_SCHEDULE_SEAT_NOT_FOUND_예외가_발생한다() {
		// given
		given(scheduleSeatRepository.findAllByIdInAndConcertScheduleId(List.of(10L, 11L), 1L))
			.willReturn(List.of(ScheduleSeatFixture.availableScheduleSeat(10L)));

		// when
		Throwable thrown = catchThrowable(
			() -> scheduleSeatCommandService.validateOccupy(1L, List.of(10L, 11L)));

		// then
		assertThat(thrown).isInstanceOf(GeneralException.class);
		assertThat(((GeneralException)thrown).getCode()).isEqualTo(ScheduleSeatErrorCode.SCHEDULE_SEAT_NOT_FOUND);
	}

	@Test
	void 이미_판매된_좌석이_포함되면_NOT_AVAILABLE_SEAT_예외가_발생한다() {
		// given
		given(scheduleSeatRepository.findAllByIdInAndConcertScheduleId(List.of(10L, 11L), 1L))
			.willReturn(List.of(
				ScheduleSeatFixture.availableScheduleSeat(10L),
				ScheduleSeatFixture.soldScheduleSeat(11L)
			));

		// when
		Throwable thrown = catchThrowable(
			() -> scheduleSeatCommandService.validateOccupy(1L, List.of(10L, 11L)));

		// then
		assertThat(thrown).isInstanceOf(GeneralException.class);
		assertThat(((GeneralException)thrown).getCode()).isEqualTo(ScheduleSeatErrorCode.NOT_AVAILABLE_SEAT);
	}
}
