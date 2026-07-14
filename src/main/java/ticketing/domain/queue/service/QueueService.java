package ticketing.domain.queue.service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

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
		String activeKey = QueueRedisKeys.activeKey(command.getConcertScheduleId());	// 대기열 -> 작업열 Key
		String userInfoKey = QueueRedisKeys.userInfoKey(command.getConcertScheduleId());

		// 1. EnterType이 JOIN(일반적인 예매하기 버튼)인 경우 -> 이미 대기열에 있거나 Active로 전환되었는지 확인 (만약 있다면 모달창 예외)
		if (command.getEnterType() == EnterType.JOIN) {
			boolean alreadyActive = redisUtil.hHasKey(activeKey, user.getId().toString());
			boolean alreadyWaiting = redisUtil.zRank(waitingKey, user.getId()) != null;
			if (alreadyActive || alreadyWaiting) {
				throw new GeneralException(QueueErrorCode.ALREADY_JOINED);
			}
		}

		// 2. EnterType이 REJOIN(모달창에서 새로 입장하기)이면 대기열 및 Active 슬롯을 여기서 제거 하도록.
		if (command.getEnterType() == EnterType.REJOIN) {
			redisUtil.opsForZSet().remove(waitingKey, user.getId());
			redisUtil.hDelete(activeKey, user.getId().toString());
		}

		// 3. 이후 줄서기
		String queueSessionId = UUID.randomUUID().toString();

		long score = redisUtil.increment(counterKey);
		redisUtil.zAdd(waitingKey, user.getId(), score);
		redisUtil.hSet(userInfoKey, user.getId().toString(), queueSessionId);	// 소유 중인 화면
		redisUtil.hExpire(userInfoKey, user.getId().toString(), SESSION_TTL);	// TTL 설정

		String token = jwtTokenUtil.generateToken(Map.of(
			"userId", user.getId(),
			"concertScheduleId", command.getConcertScheduleId(),
			"queueSessionId", queueSessionId
		));

		long rank = safeRank(redisUtil.zRank(waitingKey, user.getId()));
		long pollingIntervalMs = JitterUtil.nextPollIntervalMillis(rank);

		return EnterDTO.Result.builder()
			.token(token)
			.rank(rank)
			.pollingIntervalMs(pollingIntervalMs)
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
		String userInfoKey = QueueRedisKeys.userInfoKey(concertScheduleId);
		String activeKey = QueueRedisKeys.activeKey(concertScheduleId);

		String storedSessionId = redisUtil.hGet(userInfoKey, userId.toString());
		if (storedSessionId == null) {
			throw new GeneralException(QueueErrorCode.NOT_IN_QUEUE);	// 이미 처리된 경우
		}
		if (!queueSessionId.equals(storedSessionId)) {
			throw new GeneralException(QueueErrorCode.SESSION_REVOKED);	// 다른 화면에서 예매를 이어받은 경우 (기존 화면은 종료)
		}

		redisUtil.hExpire(userInfoKey, userId.toString(), SESSION_TTL);	// TTL 연장 하트비트

		// Active 상태인지 확인 후에 return;
		boolean isActive = redisUtil.hHasKey(activeKey, userId.toString());
		long rank = safeRank(redisUtil.zRank(waitingKey, userId));
		long pollingIntervalMs = JitterUtil.nextPollIntervalMillis(rank);

		return StatusDTO.Result.builder()
			.rank(rank)
			.pollingIntervalMs(pollingIntervalMs)
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
		String userInfoKey = QueueRedisKeys.userInfoKey(command.getConcertScheduleId());
		String activeKey = QueueRedisKeys.activeKey(command.getConcertScheduleId());

		boolean isActive = redisUtil.hHasKey(activeKey, user.getId().toString());
		boolean isWaiting = redisUtil.zRank(waitingKey, user.getId()) != null;
		if (!isActive && !isWaiting) {
			throw new GeneralException(QueueErrorCode.NOT_IN_QUEUE);
		}

		// 기존 화면 종료를 위한 queueSessionId 갱신 (순번/활성 상태는 그대로 유지)
		String queueSessionId = UUID.randomUUID().toString();
		redisUtil.hSet(userInfoKey, user.getId().toString(), queueSessionId);
		redisUtil.hExpire(userInfoKey, user.getId().toString(), SESSION_TTL);	// TTL 하트비트

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
		long pollingIntervalMs = JitterUtil.nextPollIntervalMillis(rank);

		return TakeoverDTO.Result.builder()
			.token(token)
			.rank(rank)
			.pollingIntervalMs(pollingIntervalMs)
			.isActive(false)
			.build();
	}

	/**
	 * Long → long 변환 시 null로 인한 NPE를 방지합니다.
	 * zADD 직후 곧바로 워커가 처리한 경우를 방지합니다.
	 * rank가 0-base여서 +1
	 */
	private long safeRank(Long rank) {
		return rank != null ? rank + 1 : 0L;
	}
}
