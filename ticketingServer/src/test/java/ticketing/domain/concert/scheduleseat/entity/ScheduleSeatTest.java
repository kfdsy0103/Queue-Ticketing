package ticketing.domain.concert.scheduleseat.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;

import ticketing.domain.concert.scheduleseat.exception.ScheduleSeatErrorCode;
import ticketing.fixture.ScheduleSeatFixture;
import ticketing.global.apiPayload.exception.GeneralException;

/**
 * ScheduleSeat의 판매·취소 가드가 좌석의 이중 판매를 막는지 검증한다.
 */
class ScheduleSeatTest {

	@Test
	void AVAILABLE_좌석은_SOLD로_전이된다() {
		// given
		ScheduleSeat scheduleSeat = ScheduleSeatFixture.availableScheduleSeat(10L);

		// when
		scheduleSeat.sell();

		// then
		assertThat(scheduleSeat.getSeatStatus()).isEqualTo(ScheduleSeat.SeatStatus.SOLD);
	}

	@Test
	void 이미_판매된_좌석을_다시_팔면_NOT_AVAILABLE_SEAT_예외가_발생한다() {
		// given
		ScheduleSeat scheduleSeat = ScheduleSeatFixture.soldScheduleSeat(10L);

		// when
		Throwable thrown = catchThrowable(scheduleSeat::sell);

		// then
		assertThat(thrown).isInstanceOf(GeneralException.class);
		assertThat(((GeneralException)thrown).getCode()).isEqualTo(ScheduleSeatErrorCode.NOT_AVAILABLE_SEAT);
		assertThat(scheduleSeat.getSeatStatus()).isEqualTo(ScheduleSeat.SeatStatus.SOLD);
	}

	@Test
	void SOLD_좌석은_AVAILABLE로_되돌아간다() {
		// given
		ScheduleSeat scheduleSeat = ScheduleSeatFixture.soldScheduleSeat(10L);

		// when
		scheduleSeat.cancel();

		// then
		assertThat(scheduleSeat.getSeatStatus()).isEqualTo(ScheduleSeat.SeatStatus.AVAILABLE);
	}

	@Test
	void 팔리지_않은_좌석을_취소하면_NOT_SOLD_SEAT_예외가_발생한다() {
		// given
		ScheduleSeat scheduleSeat = ScheduleSeatFixture.availableScheduleSeat(10L);

		// when
		Throwable thrown = catchThrowable(scheduleSeat::cancel);

		// then
		assertThat(thrown).isInstanceOf(GeneralException.class);
		assertThat(((GeneralException)thrown).getCode()).isEqualTo(ScheduleSeatErrorCode.NOT_SOLD_SEAT);
		assertThat(scheduleSeat.getSeatStatus()).isEqualTo(ScheduleSeat.SeatStatus.AVAILABLE);
	}

	@Test
	void 판매와_취소를_반복해도_상태가_어긋나지_않는다() {
		// given
		ScheduleSeat scheduleSeat = ScheduleSeatFixture.availableScheduleSeat(10L);

		// when
		scheduleSeat.sell();
		scheduleSeat.cancel();
		scheduleSeat.sell();

		// then
		assertThat(scheduleSeat.getSeatStatus()).isEqualTo(ScheduleSeat.SeatStatus.SOLD);
		assertThat(catchThrowable(scheduleSeat::sell)).isInstanceOf(GeneralException.class);
	}
}
