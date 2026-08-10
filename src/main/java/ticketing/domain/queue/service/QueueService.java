package ticketing.domain.queue.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.queue.constants.QueueRedisKeys;
import ticketing.domain.queue.dto.EnterDTO;
import ticketing.domain.queue.dto.StatusDTO;
import ticketing.domain.queue.dto.TakeoverDTO;
import ticketing.domain.queue.enums.EnterType;
import ticketing.domain.queue.exception.QueueErrorCode;
import ticketing.domain.user.entity.User;
import ticketing.domain.user.exception.UserErrorCode;
import ticketing.domain.user.repository.UserRepository;
import ticketing.global.apiPayload.code.GeneralErrorCode;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.util.JitterUtil;
import ticketing.global.util.JwtTokenUtil;
import ticketing.global.util.RedisUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

	// TODO: 실제 경로로 변경
	private static final String REDIRECT_ENDPOINT = "/frontend/booking";
	private static final Duration SESSION_TTL = Duration.ofMinutes(3);
	private static final Duration ACTIVE_TTL = Duration.ofMinutes(7);
	private static final long PROMOTION_BATCH_SIZE = 100;
	private static final long JITTER_FREE_POSITION = 100;
	private static final RedisScript<Long> PROMOTE_SCRIPT =
		RedisScript.of(new ClassPathResource("luaScripts/promote-queue.lua"), Long.class);

	private final UserRepository userRepository;
	private final RedisUtil redisUtil;
	private final JwtTokenUtil jwtTokenUtil;

	/**
	 * 대기열 입장을 처리합니다.
	 * 서버에서 UUID와 함께 QueueToken을 발급하고, 클라이언트는 해당 토큰을 상태 폴링에 사용합니다.
	 * UUID는 한 화면에서 대기할 수 있도록 하기 위한 장치입니다.
	 */
	public EnterDTO.Result enter(EnterDTO.Command command) {

		User user = userRepository.findById(command.getUserId())
			.orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

		String waitingKey = QueueRedisKeys.waitingKey(command.getConcertScheduleId());	// 대기열 Key
		String counterKey = QueueRedisKeys.counterKey(command.getConcertScheduleId());	// 카운터 Key
		String activeKey = QueueRedisKeys.activeKey(command.getConcertScheduleId(), user.getId());	// 대기열 -> 작업열 Key
		String userInfoKey = QueueRedisKeys.userInfoKey(command.getConcertScheduleId(), user.getId());

		// 1. EnterType이 JOIN(일반적인 예매하기 버튼)인 경우 -> 이미 대기열에 있거나 Active로 전환되었는지 확인 (만약 있다면 모달창 예외)
		if (command.getEnterType() == EnterType.JOIN) {
			boolean alreadyActive = redisUtil.hasKey(activeKey);
			boolean alreadyWaiting = redisUtil.zRank(waitingKey, user.getId()) != null;
			if (alreadyActive || alreadyWaiting) {
				throw new GeneralException(QueueErrorCode.ALREADY_JOINED);
			}
		}

		// 2. EnterType이 REJOIN(모달창에서 새로 입장하기)이면 대기열 및 Active 슬롯을 여기서 제거 하도록.
		if (command.getEnterType() == EnterType.REJOIN) {
			redisUtil.opsForZSet().remove(waitingKey, user.getId());
			redisUtil.delete(activeKey);
		}

		// 3. 이후 줄서기
		String queueSessionId = UUID.randomUUID().toString();

		long score = redisUtil.increment(counterKey);
		redisUtil.zAdd(waitingKey, user.getId(), score);
		redisUtil.set(userInfoKey, queueSessionId, SESSION_TTL);	// 소유 중인 화면 (값 + TTL 설정)

		String token = jwtTokenUtil.generateToken(Map.of(
			"userId", user.getId(),
			"concertScheduleId", command.getConcertScheduleId(),
			"queueSessionId", queueSessionId
		));

		long rank = safeRank(redisUtil.zRank(waitingKey, user.getId()));
		long retryAfterMs = nextPollIntervalMillis(rank);

		return EnterDTO.Result.builder()
			.token(token)
			.rank(rank)
			.retryAfterMs(retryAfterMs)
			.build();
	}

	/**
	 * 대기열에서 사용자의 순번 상태를 조회합니다.
	 * 토큰에 담긴 queueSessionId이 현재 Redis에 기록된 화면과 다르면 다른 화면에서 이어간 것으로 간주하여 모달창 예외가 나갑니다.
	 */
	public StatusDTO.Result status(StatusDTO.Command command) {

		Long concertScheduleId = jwtTokenUtil.getClaim(command.getToken(), "concertScheduleId", Long.class);
		Long userId = jwtTokenUtil.getClaim(command.getToken(), "userId", Long.class);
		String queueSessionId = jwtTokenUtil.getClaim(command.getToken(), "queueSessionId", String.class);

		// 토큰에 기록된 userId와 인증 객체에 담긴 userId 일치 검사
		if (!command.getUserId().equals(userId)) {
			throw new GeneralException(GeneralErrorCode.FORBIDDEN);
		}

		// 기존 userRepository.find().orElseThrow 구문은 시큐리티 단에서 이미 처리되어 검증된 상태이므로, 매 폴링마다 반복할 필요 X

		String waitingKey = QueueRedisKeys.waitingKey(concertScheduleId);
		String userInfoKey = QueueRedisKeys.userInfoKey(concertScheduleId, userId);
		String activeKey = QueueRedisKeys.activeKey(concertScheduleId, userId);

		String storedSessionId = redisUtil.get(userInfoKey);
		if (storedSessionId == null) {
			throw new GeneralException(QueueErrorCode.NOT_IN_QUEUE);	// 이미 처리된 경우
		}
		if (!queueSessionId.equals(storedSessionId)) {
			throw new GeneralException(QueueErrorCode.SESSION_REVOKED);	// 다른 화면에서 예매를 이어받은 경우 (기존 화면은 종료)
		}

		redisUtil.expire(userInfoKey, SESSION_TTL);	// TTL 연장 하트비트

		// Active 상태인지 확인 후에 return;
		boolean isActive = redisUtil.hasKey(activeKey);
		long rank = safeRank(redisUtil.zRank(waitingKey, userId));
		long retryAfterMs = nextPollIntervalMillis(rank);

		return StatusDTO.Result.builder()
			.rank(rank)
			.retryAfterMs(retryAfterMs)
			.isActive(isActive)
			.build();
	}

	/**
	 * 대기열 이어받기를 처리합니다.
	 * 기존 순번은 그대로 두고 queueSessionId만 새로 발급합니다.
	 * 기존 화면의 폴링은 status()에서 감지되어 SESSION_REVOKED로 종료됩니다.
	 */
	public TakeoverDTO.Result takeover(TakeoverDTO.Command command) {

		User user = userRepository.findById(command.getUserId())
			.orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

		String waitingKey = QueueRedisKeys.waitingKey(command.getConcertScheduleId());
		String userInfoKey = QueueRedisKeys.userInfoKey(command.getConcertScheduleId(), user.getId());
		String activeKey = QueueRedisKeys.activeKey(command.getConcertScheduleId(), user.getId());

		boolean isActive = redisUtil.hasKey(activeKey);
		boolean isWaiting = redisUtil.zRank(waitingKey, user.getId()) != null;
		if (!isActive && !isWaiting) {
			throw new GeneralException(QueueErrorCode.NOT_IN_QUEUE);
		}

		// 기존 화면 종료를 위한 queueSessionId 갱신 (순번/활성 상태는 그대로 유지)
		String queueSessionId = UUID.randomUUID().toString();
		redisUtil.set(userInfoKey, queueSessionId, SESSION_TTL);	// 값 갱신 + TTL 하트비트

		// Active로 대기열 통과했으면 점유 작업 시에도 sessionId 영향받도록 반영
		isActive = redisUtil.hasKey(activeKey);
		if (isActive) {
			redisUtil.set(activeKey, queueSessionId, ACTIVE_TTL);
		}

		String token = jwtTokenUtil.generateToken(Map.of(
			"userId", user.getId(),
			"concertScheduleId", command.getConcertScheduleId(),
			"queueSessionId", queueSessionId
		));

		if (isActive) {
			return TakeoverDTO.Result.builder()
				.token(token)
				.isActive(true)
				.redirectEndpoint(REDIRECT_ENDPOINT)
				.build();
		}

		long rank = safeRank(redisUtil.zRank(waitingKey, user.getId()));
		long retryAfterMs = nextPollIntervalMillis(rank);

		return TakeoverDTO.Result.builder()
			.token(token)
			.rank(rank)
			.retryAfterMs(retryAfterMs)
			.isActive(false)
			.build();
	}

	/**
	 * concertSchedulerId에 해당하는 대기열의 앞 쪽에서 PROMOTION_BATCH_SIZE 만큼을 작업열로 이동시킵니다.
	 * 이때, ZPOPMIN + 작업열 등록은 Lua로 처리합니다.
	 * 	이는 Enter API에서 발생하는 상태 체크 시 Race Condition을 방지하기 위함입니다.
	 */
	public Long promote(Long concertScheduleId) {

		String waitingKey = QueueRedisKeys.waitingKey(concertScheduleId);
		String activeKeyPrefix = QueueRedisKeys.activeKeyPrefix(concertScheduleId);
		String userInfoKeyPrefix = QueueRedisKeys.userInfoKeyPrefix(concertScheduleId);

		// '대기열 -> 작업열로 Active 활성화'
		return redisUtil.execute(
			PROMOTE_SCRIPT,
			List.of(waitingKey, activeKeyPrefix, userInfoKeyPrefix),
			PROMOTION_BATCH_SIZE,
			ACTIVE_TTL.toSeconds()
		);
	}

	/**
	 * Long → long 변환 시 null로 인한 NPE를 방지합니다.
	 * zADD 직후 곧바로 워커가 처리한 경우를 방지합니다.
	 * rank가 0-base여서 +1
	 */
	private long safeRank(Long rank) {
		return rank != null ? rank + 1 : 0L;
	}

	/**
	 * 대기 순번(position)에 따라 Polling 주기를 차등 적용하고, Jitter를 더해 동시 Polling을 분산한 최종 주기를 반환합니다. (ms)
	 * 곧 입장할 유저는 짧은 주기로 체감을 개선하고, 뒤쪽 유저는 긴 주기로 서버 부하를 억제합니다.
	 */
	private long nextPollIntervalMillis(long position) {
		Duration interval = getInterval(position);
		if (position <= JITTER_FREE_POSITION) {
			return interval.toMillis();
		}
		return JitterUtil.applyJitter(interval).toMillis();
	}

	// position 기준 Polling 주기
	private Duration getInterval(long position) {
		if (position <= JITTER_FREE_POSITION) return Duration.ofSeconds(1);  // 곧 입장 — 1초
		if (position <= 1000) return Duration.ofSeconds(2);
		if (position <= 10000) return Duration.ofSeconds(5);
		if (position <= 100000) return Duration.ofSeconds(10);
		return Duration.ofSeconds(30);                                       // 9.5분+ 대기 — 30초
	}
}
