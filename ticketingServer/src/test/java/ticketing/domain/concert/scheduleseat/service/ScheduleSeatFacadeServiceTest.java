package ticketing.domain.concert.scheduleseat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;

import ticketing.domain.concert.scheduleseat.constants.ScheduleSeatRedisKeys;
import ticketing.domain.concert.scheduleseat.dto.FindAllDTO;
import ticketing.domain.concert.scheduleseat.dto.FindMyOccupyDTO;
import ticketing.domain.concert.scheduleseat.dto.FindRemainingDTO;
import ticketing.domain.concert.scheduleseat.dto.OccupyDTO;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.concert.scheduleseat.enums.SeatDisplayStatus;
import ticketing.domain.concert.scheduleseat.exception.ScheduleSeatErrorCode;
import ticketing.domain.queue.constants.QueueRedisKeys;
import ticketing.domain.queue.exception.QueueErrorCode;
import ticketing.fixture.ScheduleSeatFixture;
import ticketing.global.apiPayload.code.GeneralErrorCode;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.util.JwtTokenUtil;
import ticketing.global.util.RedisUtil;

/**
 * ScheduleSeatFacadeService의 토큰·대기열 세션 검증, 좌석 점유 원자성, 좌석 상태 분류를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleSeatFacadeServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long SCHEDULE_ID = 1L;
	private static final String TOKEN = "token";
	private static final String SESSION_ID = "session-1";

	@InjectMocks
	private ScheduleSeatFacadeService scheduleSeatFacadeService;

	@Mock
	private ScheduleSeatQueryService scheduleSeatQueryService;

	@Mock
	private ScheduleSeatCommandService scheduleSeatCommandService;

	@Mock
	private RedisUtil redisUtil;

	@Mock
	private JwtTokenUtil jwtTokenUtil;

	@Captor
	private ArgumentCaptor<Map<Long, LocalDateTime>> expireTimeCaptor;

	private void givenTokenOwnerIs(Long tokenUserId) {
		given(jwtTokenUtil.getClaim(TOKEN, "userId", Long.class)).willReturn(tokenUserId);
	}

	private void givenActiveSession(String storedSessionId, String tokenSessionId) {
		given(redisUtil.get(QueueRedisKeys.activeKey(SCHEDULE_ID, USER_ID))).willReturn(storedSessionId);
		given(jwtTokenUtil.getClaim(TOKEN, "queueSessionId", String.class)).willReturn(tokenSessionId);
	}

	private void givenOccupiedSeatIds(Set<Object> members) {
		given(redisUtil.zRangeByScoreFrom(
			eq(ScheduleSeatRedisKeys.scheduleOccupyKey(SCHEDULE_ID)), anyDouble()
		)).willReturn(members);
	}

	private static OccupyDTO.Command occupyCommand(List<Long> scheduleSeatIds) {
		return OccupyDTO.Command.builder()
			.userId(USER_ID)
			.concertScheduleId(SCHEDULE_ID)
			.scheduleSeatIds(scheduleSeatIds)
			.token(TOKEN)
			.build();
	}

	@Nested
	@DisplayName("occupy")
	class Occupy {

		@Test
		void 토큰의_userId가_요청자와_다르면_FORBIDDEN_예외가_발생한다() {
			// given
			givenTokenOwnerIs(99L);

			// when
			Throwable thrown = catchThrowable(() -> scheduleSeatFacadeService.occupy(occupyCommand(List.of(10L))));

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(GeneralErrorCode.FORBIDDEN);
		}

		@Test
		void 대기열을_통과하지_않았으면_NOT_ACTIVE_예외가_발생한다() {
			// given
			givenTokenOwnerIs(USER_ID);
			given(redisUtil.get(QueueRedisKeys.activeKey(SCHEDULE_ID, USER_ID))).willReturn(null);

			// when
			Throwable thrown = catchThrowable(() -> scheduleSeatFacadeService.occupy(occupyCommand(List.of(10L))));

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(QueueErrorCode.NOT_ACTIVE);
		}

		@Test
		void 저장된_세션과_토큰_세션이_다르면_SESSION_REVOKED_예외가_발생한다() {
			// given
			givenTokenOwnerIs(USER_ID);
			givenActiveSession(SESSION_ID, "session-2");

			// when
			Throwable thrown = catchThrowable(() -> scheduleSeatFacadeService.occupy(occupyCommand(List.of(10L))));

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(QueueErrorCode.SESSION_REVOKED);
		}

		@Test
		void 하나라도_이미_점유된_좌석이_있으면_ALREADY_OCCUPIED_예외가_발생한다() {
			// given
			givenTokenOwnerIs(USER_ID);
			givenActiveSession(SESSION_ID, SESSION_ID);
			given(redisUtil.<Long>execute(any(), anyList(), any(Object[].class))).willReturn(0L);

			// when
			Throwable thrown = catchThrowable(
				() -> scheduleSeatFacadeService.occupy(occupyCommand(List.of(10L, 11L))));

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(ScheduleSeatErrorCode.ALREADY_OCCUPIED);
		}

		@Test
		void 점유에_성공하면_좌석_ID와_만료_시각이_반환된다() {
			// given
			givenTokenOwnerIs(USER_ID);
			givenActiveSession(SESSION_ID, SESSION_ID);
			given(redisUtil.<Long>execute(any(), anyList(), any(Object[].class))).willReturn(1L);

			// when
			OccupyDTO.Result result = scheduleSeatFacadeService.occupy(occupyCommand(List.of(10L, 11L)));

			// then
			assertThat(result.getScheduleSeatIds()).containsExactly(10L, 11L);
			assertThat(result.getExpiresAt()).isAfter(LocalDateTime.now());
			verify(scheduleSeatCommandService).validateOccupy(SCHEDULE_ID, List.of(10L, 11L));
		}
	}

	@Nested
	@DisplayName("findAll")
	class FindAll {

		@Test
		void 좌석은_SOLD와_OCCUPIED와_AVAILABLE로_분류되어_반환된다() {
			// given
			givenTokenOwnerIs(USER_ID);
			givenActiveSession(SESSION_ID, SESSION_ID);
			given(scheduleSeatQueryService.findSeatStatuses(SCHEDULE_ID)).willReturn(List.of(
				ScheduleSeatFixture.layoutItem(10L, 100L, ScheduleSeat.SeatStatus.SOLD),
				ScheduleSeatFixture.layoutItem(11L, 101L, ScheduleSeat.SeatStatus.AVAILABLE),
				ScheduleSeatFixture.layoutItem(12L, 102L, ScheduleSeat.SeatStatus.AVAILABLE)
			));
			givenOccupiedSeatIds(Set.of("11"));

			// when
			FindAllDTO.Result result = scheduleSeatFacadeService.findAll(
				FindAllDTO.Command.of(USER_ID, SCHEDULE_ID, TOKEN));

			// then
			assertThat(result.getScheduleSeats())
				.extracting(FindAllDTO.ScheduleSeatInfo::getScheduleSeatId, FindAllDTO.ScheduleSeatInfo::getSeatStatus)
				.containsExactly(
					tuple(10L, SeatDisplayStatus.SOLD),
					tuple(11L, SeatDisplayStatus.OCCUPIED),
					tuple(12L, SeatDisplayStatus.AVAILABLE)
				);
		}
	}

	@Nested
	@DisplayName("findRemaining")
	class FindRemaining {

		@Test
		void 등급별_잔여_좌석은_점유_중인_좌석을_제외하고_집계된다() {
			// given
			given(scheduleSeatQueryService.findAvailableSeats(SCHEDULE_ID)).willReturn(List.of(
				ScheduleSeatFixture.gradeProjection(10L, 1L, "VIP"),
				ScheduleSeatFixture.gradeProjection(11L, 1L, "VIP"),
				ScheduleSeatFixture.gradeProjection(12L, 2L, "R")
			));
			givenOccupiedSeatIds(Set.of("11"));

			// when
			FindRemainingDTO.Result result = scheduleSeatFacadeService.findRemaining(
				FindRemainingDTO.Command.of(SCHEDULE_ID));

			// then
			assertThat(result.getSeatGrades())
				.extracting(
					FindRemainingDTO.Result.SeatGradeRemaining::getSeatGradeName,
					FindRemainingDTO.Result.SeatGradeRemaining::getRemainingSeatCount)
				.containsExactly(
					tuple("VIP", 1L),
					tuple("R", 1L)
				);
		}
	}

	@Nested
	@DisplayName("findMyOccupy")
	class FindMyOccupy {

		@Test
		void 만료된_점유_좌석은_결과에서_제외된다() {
			// given
			long now = System.currentTimeMillis();
			given(redisUtil.zRangeWithScores(ScheduleSeatRedisKeys.userOccupyKey(USER_ID))).willReturn(Set.of(
				new DefaultTypedTuple<>("10", (double)(now + 60_000)),
				new DefaultTypedTuple<>("11", (double)(now - 1_000))
			));
			given(scheduleSeatQueryService.findMyOccupySeats(any())).willReturn(List.of());

			// when
			scheduleSeatFacadeService.findMyOccupy(FindMyOccupyDTO.Command.of(USER_ID));

			// then
			verify(scheduleSeatQueryService).findMyOccupySeats(expireTimeCaptor.capture());
			assertThat(expireTimeCaptor.getValue()).containsOnlyKeys(10L);
		}

		@Test
		void 점유_중인_좌석이_없으면_빈_결과가_반환된다() {
			// given
			given(redisUtil.zRangeWithScores(ScheduleSeatRedisKeys.userOccupyKey(USER_ID))).willReturn(Set.of());

			// when
			FindMyOccupyDTO.Result result = scheduleSeatFacadeService.findMyOccupy(
				FindMyOccupyDTO.Command.of(USER_ID));

			// then
			assertThat(result.getSeats()).isEmpty();
		}
	}
}
