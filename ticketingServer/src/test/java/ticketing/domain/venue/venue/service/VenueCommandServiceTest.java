package ticketing.domain.venue.venue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ticketing.domain.seatgrade.exception.SeatGradeErrorCode;
import ticketing.domain.seatgrade.repository.SeatGradeRepository;
import ticketing.domain.venue.seat.entity.Seat;
import ticketing.domain.venue.seat.repository.SeatRepository;
import ticketing.domain.venue.venue.dto.CreateDTO;
import ticketing.domain.venue.venue.repository.VenueRepository;
import ticketing.global.apiPayload.exception.GeneralException;

/**
 * VenueCommandService가 좌석을 등급 이름으로 매핑하여 저장하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class VenueCommandServiceTest {

	@InjectMocks
	private VenueCommandService venueCommandService;

	@Mock
	private VenueRepository venueRepository;

	@Mock
	private SeatRepository seatRepository;

	@Mock
	private SeatGradeRepository seatGradeRepository;

	@Captor
	private ArgumentCaptor<List<Seat>> seatsCaptor;

	private static CreateDTO.Command createCommand(String seatGradeNameOfSecondSeat) {
		return CreateDTO.Command.builder()
			.name("올림픽공원 체조경기장")
			.seatGrades(List.of(
				CreateDTO.Command.SeatGradeCommand.builder().name("VIP").build(),
				CreateDTO.Command.SeatGradeCommand.builder().name("R").build()
			))
			.seats(List.of(
				CreateDTO.Command.SeatCommand.builder().seatGradeName("VIP").seatNumber("A1").build(),
				CreateDTO.Command.SeatCommand.builder().seatGradeName(seatGradeNameOfSecondSeat).seatNumber("A2").build()
			))
			.build();
	}

	@Test
	void 좌석은_등급_이름으로_매핑되어_저장된다() {
		// when
		CreateDTO.Result result = venueCommandService.create(createCommand("R"));

		// then
		verify(venueRepository).save(any());
		verify(seatRepository).saveAll(seatsCaptor.capture());
		assertThat(seatsCaptor.getValue())
			.extracting(Seat::getSeatNumber, seat -> seat.getSeatGrade().getName())
			.containsExactly(
				tuple("A1", "VIP"),
				tuple("A2", "R")
			);
		assertThat(result.getName()).isEqualTo("올림픽공원 체조경기장");
		assertThat(result.getSeatGrades()).hasSize(2);
	}

	@Test
	void 등급_목록에_없는_이름을_좌석에_쓰면_SEAT_GRADE_NOT_FOUND_예외가_발생한다() {
		// when
		Throwable thrown = catchThrowable(() -> venueCommandService.create(createCommand("존재하지_않는_등급")));

		// then
		assertThat(thrown).isInstanceOf(GeneralException.class);
		assertThat(((GeneralException)thrown).getCode()).isEqualTo(SeatGradeErrorCode.SEAT_GRADE_NOT_FOUND);
		verify(seatRepository, never()).saveAll(any());
	}
}
