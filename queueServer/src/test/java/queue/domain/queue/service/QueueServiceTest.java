package queue.domain.queue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ZSetOperations;

import io.jsonwebtoken.Claims;
import queue.domain.queue.constants.QueueRedisKeys;
import queue.domain.queue.dto.EnterDTO;
import queue.domain.queue.dto.StatusDTO;
import queue.domain.queue.dto.TakeoverDTO;
import queue.domain.queue.enums.EnterType;
import queue.domain.queue.exception.QueueErrorCode;
import queue.fixture.QueueFixture;
import queue.global.apiPayload.code.GeneralErrorCode;
import queue.global.apiPayload.exception.GeneralException;
import queue.global.util.JwtTokenUtil;
import queue.global.util.RedisUtil;

/**
 * QueueService의 입장·상태조회·이어받기 검증 분기와 순번 구간별 폴링 주기 계산을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long SCHEDULE_ID = 1L;
	private static final String SESSION_ID = "sess-1";
	private static final Duration SESSION_TTL = Duration.ofMinutes(3);
	private static final Duration ACTIVE_TTL = Duration.ofMinutes(7);

	private static final String WAITING_KEY = QueueRedisKeys.waitingKey(SCHEDULE_ID);
	private static final String COUNTER_KEY = QueueRedisKeys.counterKey(SCHEDULE_ID);
	private static final String ACTIVE_KEY = QueueRedisKeys.activeKey(SCHEDULE_ID, USER_ID);
	private static final String USER_INFO_KEY = QueueRedisKeys.userInfoKey(SCHEDULE_ID, USER_ID);

	@Mock
	private RedisUtil redisUtil;

	@Mock
	private JwtTokenUtil jwtTokenUtil;

	@Mock
	private ZSetOperations<String, Object> zSetOperations;

	@Captor
	private ArgumentCaptor<Map<String, Object>> claimsCaptor;

	@Captor
	private ArgumentCaptor<String> sessionIdCaptor;

	// activeTtlSeconds 가 생성자로 주입되는 primitive 라 @InjectMocks 로는 채울 수 없다.
	private QueueService queueService;

	@BeforeEach
	void setUp() {
		queueService = new QueueService(redisUtil, jwtTokenUtil, ACTIVE_TTL.toSeconds());
	}

	private void givenTokenClaims(Long tokenUserId, String tokenSessionId) {
		Claims claims = mock(Claims.class);
		given(claims.get("concertScheduleId", Long.class)).willReturn(SCHEDULE_ID);
		given(claims.get("userId", Long.class)).willReturn(tokenUserId);
		given(claims.get("queueSessionId", String.class)).willReturn(tokenSessionId);

		given(jwtTokenUtil.parseClaims(QueueFixture.TOKEN)).willReturn(claims);
	}

	@Nested
	@DisplayName("enter")
	class Enter {

		@Test
		void JOIN인데_이미_작업열에_있으면_ALREADY_JOINED_예외가_발생한다() {
			// given
			given(redisUtil.hasKey(ACTIVE_KEY)).willReturn(true);
			given(redisUtil.zRank(WAITING_KEY, USER_ID)).willReturn(null);

			// when
			Throwable thrown = catchThrowable(
				() -> queueService.enter(QueueFixture.enterCommand(USER_ID, SCHEDULE_ID, EnterType.JOIN)));

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(QueueErrorCode.ALREADY_JOINED);
			verify(redisUtil, never()).increment(anyString());
		}

		@Test
		void JOIN인데_이미_대기열에_있으면_ALREADY_JOINED_예외가_발생한다() {
			// given
			given(redisUtil.hasKey(ACTIVE_KEY)).willReturn(false);
			given(redisUtil.zRank(WAITING_KEY, USER_ID)).willReturn(5L);

			// when
			Throwable thrown = catchThrowable(
				() -> queueService.enter(QueueFixture.enterCommand(USER_ID, SCHEDULE_ID, EnterType.JOIN)));

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(QueueErrorCode.ALREADY_JOINED);
			verify(redisUtil, never()).increment(anyString());
		}

		@Test
		void REJOIN이면_기존_대기열과_작업열_슬롯을_지우고_새로_줄선다() {
			// given
			given(redisUtil.opsForZSet()).willReturn(zSetOperations);
			given(redisUtil.increment(COUNTER_KEY)).willReturn(7L);
			given(redisUtil.zRank(WAITING_KEY, USER_ID)).willReturn(6L);

			// when
			queueService.enter(QueueFixture.enterCommand(USER_ID, SCHEDULE_ID, EnterType.REJOIN));

			// then
			verify(zSetOperations).remove(WAITING_KEY, USER_ID);
			verify(redisUtil).delete(ACTIVE_KEY);
			verify(redisUtil).zAddIfAbsent(WAITING_KEY, USER_ID, 7.0);
		}

		@Test
		void 카운터로_받은_순번으로_줄서고_저장된_세션ID가_토큰에도_담긴다() {
			// given
			given(redisUtil.hasKey(ACTIVE_KEY)).willReturn(false);
			given(redisUtil.zRank(WAITING_KEY, USER_ID)).willReturn(null, 6L);
			given(redisUtil.increment(COUNTER_KEY)).willReturn(7L);

			// when
			EnterDTO.Result result = queueService.enter(
				QueueFixture.enterCommand(USER_ID, SCHEDULE_ID, EnterType.JOIN));

			// then
			verify(redisUtil).zAddIfAbsent(WAITING_KEY, USER_ID, 7.0);
			verify(redisUtil).set(eq(USER_INFO_KEY), sessionIdCaptor.capture(), eq(SESSION_TTL));
			verify(jwtTokenUtil).generateToken(claimsCaptor.capture());

			assertThat(claimsCaptor.getValue())
				.containsEntry("userId", USER_ID)
				.containsEntry("concertScheduleId", SCHEDULE_ID)
				.containsEntry("queueSessionId", sessionIdCaptor.getValue());
			assertThat(result.getRank()).isEqualTo(7L);
		}
	}

	@Nested
	@DisplayName("status")
	class Status {

		@Test
		void 토큰의_userId와_요청자가_다르면_FORBIDDEN_예외가_발생한다() {
			// given
			givenTokenClaims(99L, SESSION_ID);

			// when
			Throwable thrown = catchThrowable(() -> queueService.status(QueueFixture.statusCommand(USER_ID)));

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(GeneralErrorCode.FORBIDDEN);
		}

		@Test
		void 세션_정보가_없으면_NOT_IN_QUEUE_예외가_발생한다() {
			// given
			givenTokenClaims(USER_ID, SESSION_ID);
			given(redisUtil.get(USER_INFO_KEY)).willReturn(null);

			// when
			Throwable thrown = catchThrowable(() -> queueService.status(QueueFixture.statusCommand(USER_ID)));

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(QueueErrorCode.NOT_IN_QUEUE);
		}

		@Test
		void 저장된_세션과_토큰_세션이_다르면_SESSION_REVOKED_예외가_발생한다() {
			// given
			givenTokenClaims(USER_ID, SESSION_ID);
			given(redisUtil.get(USER_INFO_KEY)).willReturn("sess-2");

			// when
			Throwable thrown = catchThrowable(() -> queueService.status(QueueFixture.statusCommand(USER_ID)));

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(QueueErrorCode.SESSION_REVOKED);
			verify(redisUtil, never()).expire(anyString(), any(Duration.class));
		}

		@Test
		void 폴링할_때마다_세션_TTL이_연장되고_작업열_여부가_반환된다() {
			// given
			givenTokenClaims(USER_ID, SESSION_ID);
			given(redisUtil.get(USER_INFO_KEY)).willReturn(SESSION_ID);
			given(redisUtil.hasKey(ACTIVE_KEY)).willReturn(true);
			given(redisUtil.zRank(WAITING_KEY, USER_ID)).willReturn(4L);

			// when
			StatusDTO.Result result = queueService.status(QueueFixture.statusCommand(USER_ID));

			// then
			verify(redisUtil).expire(USER_INFO_KEY, SESSION_TTL);
			assertThat(result.isActive()).isTrue();
			assertThat(result.getRank()).isEqualTo(5L);
		}

		@Test
		void 대기열에서_빠져나갔으면_순번이_0으로_반환된다() {
			// given
			givenTokenClaims(USER_ID, SESSION_ID);
			given(redisUtil.get(USER_INFO_KEY)).willReturn(SESSION_ID);
			given(redisUtil.hasKey(ACTIVE_KEY)).willReturn(true);
			given(redisUtil.zRank(WAITING_KEY, USER_ID)).willReturn(null);

			// when
			StatusDTO.Result result = queueService.status(QueueFixture.statusCommand(USER_ID));

			// then
			assertThat(result.getRank()).isZero();
		}
	}

	@Nested
	@DisplayName("takeover")
	class Takeover {

		@Test
		void 대기열에도_작업열에도_없으면_NOT_IN_QUEUE_예외가_발생한다() {
			// given
			given(redisUtil.hasKey(ACTIVE_KEY)).willReturn(false);
			given(redisUtil.zRank(WAITING_KEY, USER_ID)).willReturn(null);

			// when
			Throwable thrown = catchThrowable(
				() -> queueService.takeover(QueueFixture.takeoverCommand(USER_ID, SCHEDULE_ID)));

			// then
			assertThat(thrown).isInstanceOf(GeneralException.class);
			assertThat(((GeneralException)thrown).getCode()).isEqualTo(QueueErrorCode.NOT_IN_QUEUE);
			verify(redisUtil, never()).set(anyString(), anyString(), any(Duration.class));
		}

		@Test
		void 작업열_상태면_작업열_키도_새_세션으로_갱신되고_리다이렉트_경로가_반환된다() {
			// given
			given(redisUtil.hasKey(ACTIVE_KEY)).willReturn(true);

			// when
			TakeoverDTO.Result result = queueService.takeover(
				QueueFixture.takeoverCommand(USER_ID, SCHEDULE_ID));

			// then
			verify(redisUtil).set(eq(USER_INFO_KEY), sessionIdCaptor.capture(), eq(SESSION_TTL));
			verify(redisUtil).set(eq(ACTIVE_KEY), eq(sessionIdCaptor.getValue()), eq(ACTIVE_TTL));
			assertThat(result.isActive()).isTrue();
			assertThat(result.getRedirectEndpoint()).isNotBlank();
		}

		@Test
		void 대기_상태면_순번은_유지되고_세션만_새로_발급된다() {
			// given
			given(redisUtil.hasKey(ACTIVE_KEY)).willReturn(false);
			given(redisUtil.zRank(WAITING_KEY, USER_ID)).willReturn(9L);

			// when
			TakeoverDTO.Result result = queueService.takeover(
				QueueFixture.takeoverCommand(USER_ID, SCHEDULE_ID));

			// then
			verify(redisUtil).set(eq(USER_INFO_KEY), anyString(), eq(SESSION_TTL));
			verify(redisUtil, never()).set(eq(ACTIVE_KEY), anyString(), any(Duration.class));
			verify(redisUtil, never()).zAddIfAbsent(anyString(), any(), anyDouble());
			assertThat(result.isActive()).isFalse();
			assertThat(result.getRank()).isEqualTo(10L);
		}
	}

	@Nested
	@DisplayName("폴링 주기")
	class PollInterval {

		/**
		 * safeRank()가 zRank + 1을 하므로 zRank 99가 순번 100(지터 제외 경계)이 된다.
		 */
		@ParameterizedTest
		@CsvSource({
			"0,      1000",
			"99,     1000",
			"100,    2000",
			"999,    2000",
			"1000,   5000",
			"9999,   5000",
			"10000,  10000",
			"99999,  10000",
			"100000, 30000"
		})
		void 순번_구간에_따라_폴링_주기가_차등_적용된다(long zRank, long baseMillis) {
			// given
			givenTokenClaims(USER_ID, SESSION_ID);
			given(redisUtil.get(USER_INFO_KEY)).willReturn(SESSION_ID);
			given(redisUtil.hasKey(ACTIVE_KEY)).willReturn(false);
			given(redisUtil.zRank(WAITING_KEY, USER_ID)).willReturn(zRank);

			// when
			StatusDTO.Result result = queueService.status(QueueFixture.statusCommand(USER_ID));

			// then
			assertThat(result.getRetryAfterMs())
				.isBetween(baseMillis - baseMillis / 10, baseMillis + baseMillis / 10);
		}

		@Test
		void 순번_100까지는_지터_없이_정확히_1초가_반환된다() {
			// given
			givenTokenClaims(USER_ID, SESSION_ID);
			given(redisUtil.get(USER_INFO_KEY)).willReturn(SESSION_ID);
			given(redisUtil.hasKey(ACTIVE_KEY)).willReturn(false);
			given(redisUtil.zRank(WAITING_KEY, USER_ID)).willReturn(99L);

			// when
			StatusDTO.Result result = queueService.status(QueueFixture.statusCommand(USER_ID));

			// then
			assertThat(result.getRetryAfterMs()).isEqualTo(1000L);
		}
	}
}
