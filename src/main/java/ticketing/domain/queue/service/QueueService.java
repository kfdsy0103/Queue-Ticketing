package ticketing.domain.queue.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.queue.constants.QueueRedisKeys;
import ticketing.domain.queue.dto.EnterDTO;
import ticketing.domain.queue.dto.StatusDTO;
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
	private static final String REDIRECT_ENDPOINT = "/api/v1/booking";

	private final UserRepository userRepository;
	private final RedisUtil redisUtil;
	private final JwtTokenUtil jwtTokenUtil;

	/**
	 * 대기열 입장을 처리합니다.
	 * 		enter() 유형은 다음 3가지로 분류됩니다.
	 * 			1. NORMAL : 일반적인 '예매하기' 버튼을 눌러 대기열에 입장하는 경우
	 * 			2. RESUME : 다른 브라우저에서 이미 순번을 받아놨고, 선택창에서 '아니오, 기존 예매를 유지합니다.' 를 클릭한 경우
	 * 			3. REJOIN : 다른 브라우저에서 이미 순번을 받아놨고, 선택창에서 '네, 새로운 예매를 진행합니다.' 를 클릭한 경우
	 */
	public EnterDTO.Result enter(EnterDTO.Command command) {

		// 유저 검증
		User user = userRepository.findById(command.getUserId())
			.orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

		String queueKey = QueueRedisKeys.waitingKey(command.getConcertScheduleId());
		String counterKey = QueueRedisKeys.counterKey(command.getConcertScheduleId());
		String sessionKey = QueueRedisKeys.sessionKey(command.getConcertScheduleId());
		String activeKey = QueueRedisKeys.activeKey(command.getConcertScheduleId());

		// 동일 화면(기기)인지 구분하기 위함
		String sessionId = command.getIdempotentKey();

		// 대기열 토큰 생성
		String token = jwtTokenUtil.generateToken(Map.of(
			"userId", user.getId(),
			"concertScheduleId", command.getConcertScheduleId(),
			"sessionId", sessionId
		));

		// 이미 대기열을 통과해 활성화(Active)된 사용자인지 확인
		String activeSessionId = redisUtil.hGet(activeKey, user.getId().toString());
		boolean isActive = activeSessionId != null;

		// 1. NORMAL - 일반적인 예매하기 버튼 입장
		if (command.getEnterType().equals(EnterType.NORMAL)) {

			// 1. 이미 active인데 같은 화면인 경우 → 즉시 통과하도록
			if (isActive && sessionId.equals(activeSessionId)) {
				return EnterDTO.Result.builder()
					.token(token)
					.needToChoose(false)
					.rank(0L)
					.pollingIntervalMs(0L)
					.build();
			}

			// 2. 이미 active인데 다른 화면인 경우 → 선택지 모달 노출되도록 true
			if (isActive) {
				return EnterDTO.Result.builder()
					.token(token)
					.needToChoose(true)
					.rank(0L)
					.pollingIntervalMs(0L)
					.build();
			}

			Double existingScore = redisUtil.zScore(queueKey, user.getId());
			boolean isWaiting = existingScore != null;

			String waitingSessionId = isWaiting ? redisUtil.hGet(sessionKey, user.getId().toString()) : null;

			// 3. 대기열에 이미 있는데 같은 화면인 경우 (따닥) -> SETNX 오버헤드없이 덮어쓰도록 단순화
			if (isWaiting && sessionId.equals(waitingSessionId)) {
				return issueRankAndEnter(queueKey, counterKey, sessionKey, user.getId(), sessionId, token);
			}

			// 4. 대기열에 이미 있는데 다른 화면에서 재진입한 경우 → 선택지 모달 true
			if (isWaiting) {
				return EnterDTO.Result.builder()
					.token(token)
					.needToChoose(true)
					.rank(0L)
					.pollingIntervalMs(0L)
					.build();
			}

			// 5. 일반적인 경우 -> 대기열 신규 입장
			return issueRankAndEnter(queueKey, counterKey, sessionKey, user.getId(), sessionId, token);
		}

		// 2. RESUME - 모달에서 '기존 예매 유지'를 선택한 경우 기존 상태 유지하고 소유 화면만 현재 화면으로 이전
		if (EnterType.RESUME.equals(command.getEnterType())) {

			// 기존 세션이 이미 active였다면 소유 화면만 이전하고 즉시 통과
			if (isActive) {
				redisUtil.hSet(activeKey, user.getId().toString(), sessionId);
				redisUtil.hSet(sessionKey, user.getId().toString(), sessionId); // status()가 검증하는 소유 화면도 함께 이전

				return EnterDTO.Result.builder()
					.token(token)
					.needToChoose(false)
					.rank(0L)
					.pollingIntervalMs(0L)
					.build();
			}

			Long rank = redisUtil.zRank(queueKey, user.getId());
			if (rank == null) {
				throw new GeneralException(QueueErrorCode.NOT_IN_QUEUE); // 이미 처리되었거나 없는 경우
			}

			redisUtil.hSet(sessionKey, user.getId().toString(), sessionId); // 소유 화면 이전(덮어쓰기)

			long pollingIntervalMs = JitterUtil.nextPollIntervalMillis(rank);

			return EnterDTO.Result.builder()
				.token(token)
				.needToChoose(false)
				.rank(rank)
				.pollingIntervalMs(pollingIntervalMs)
				.build();
		}

		// 3. REJOIN - 모달에서 '새로운 예매 진행'을 선택한 경우, 기존 예매 종료 후 순번 재발급
		if (EnterType.REJOIN.equals(command.getEnterType())) {

			// 기존 세션이 이미 Active였다면 Active 슬롯 삭제
			if (isActive) {
				redisUtil.hDel(activeKey, user.getId().toString());
			}

			return issueRankAndEnter(queueKey, counterKey, sessionKey, user.getId(), sessionId, token);
		}

		throw new GeneralException(QueueErrorCode.INVALID_ENTER_TYPE);
	}

	/**
	 * 대기열에서 사용자의 순번 상태를 조회합니다.
	 * 토큰에 담긴 화면(기기) 정보가 현재 등록된 화면과 다르면 다른 화면에서 이미 처리 중인 것으로 간주합니다.
	 * 1 계정 - 1 화면 - 1 대기열
	 */
	public StatusDTO.Result status(StatusDTO.Command command) {

		Long concertScheduleId = jwtTokenUtil.getClaim(command.getToken(), "concertScheduleId", Long.class);
		Long userId = jwtTokenUtil.getClaim(command.getToken(), "userId", Long.class);
		String sessionId = jwtTokenUtil.getClaim(command.getToken(), "sessionId", String.class);

		// 토큰에 담긴 유저와 요청한 유저가 불일치하는 경우
		if (!command.getUserId().equals(userId)) {
			throw new GeneralException(GeneralErrorCode.FORBIDDEN);
		}

		User user = userRepository.findById(userId)
			.orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

		String queueKey = QueueRedisKeys.waitingKey(concertScheduleId);
		String sessionKey = QueueRedisKeys.sessionKey(concertScheduleId);
		String activeKey = QueueRedisKeys.activeKey(concertScheduleId);

		// 하나의 화면에서만 대기열 폴링이 가능하도록 방어
		String registeredSessionId = redisUtil.hGet(sessionKey, user.getId().toString());
		if (!sessionId.equals(registeredSessionId)) {
			throw new GeneralException(QueueErrorCode.DIFFERENT_SESSION);	// 다른 화면(기기)에서 처리 중입니다.
		}

		// 워커에 의해 '대기열 -> 작업열'로 승격된 사용자인지 확인
		boolean isActive = redisUtil.hGet(activeKey, user.getId().toString()) != null;

		if (isActive) {
			return StatusDTO.Result.builder()
				.isActive(true)
				.redirectEndpoint(REDIRECT_ENDPOINT)
				.build();
		}

		// 아직 승격되지 않았다면 isActive(false) + polling 하도록
		Long rank = redisUtil.zRank(queueKey, user.getId());
		if (rank == null) {
			throw new GeneralException(QueueErrorCode.NOT_IN_QUEUE);
		}

		long pollingIntervalMs = JitterUtil.nextPollIntervalMillis(rank);

		return StatusDTO.Result.builder()
			.rank(rank)
			.pollingIntervalMs(pollingIntervalMs)
			.isActive(false)
			.build();
	}

	/**
	 * 새로운 순번을 발급(또는 갱신)하고 대기열에 등록한 뒤 결과를 반환합니다.
	 */
	private EnterDTO.Result issueRankAndEnter(
		String queueKey,
		String counterKey,
		String sessionKey,
		Long userId,
		String sessionId,
		String token
	) {
		long score = redisUtil.increment(counterKey);
		redisUtil.zAdd(queueKey, userId, score);
		redisUtil.hSet(sessionKey, userId.toString(), sessionId); // 대기열을 가져간 화면(기기) 기록

		long rank = safeRank(redisUtil.zRank(queueKey, userId));
		long pollingIntervalMs = JitterUtil.nextPollIntervalMillis(rank);

		return EnterDTO.Result.builder()
			.token(token)
			.needToChoose(false)
			.rank(rank)
			.pollingIntervalMs(pollingIntervalMs)
			.build();
	}

	/**
	 * Long → long 변환 시 null로 인한 NPE를 방지합니다.
	 * zADD 직후 곧바로 워커가 처리한 경우를 방지합니다.
	 */
	private long safeRank(Long rank) {
		return rank != null ? rank : 0L;
	}
}
