package ticketing.domain.queue.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

		String queueKey = "queue:concertSchedule:" + command.getConcertScheduleId();
		String counterKey = queueKey + ":counter";
		String sessionKey = queueKey + ":session";

		// 동일 화면(기기)인지 구분하기 위함
		String sessionId = command.getIdempotentKey();

		// 대기열 토큰 생성
		String token = jwtTokenUtil.generateToken(Map.of(
			"userId", user.getId(),
			"concertScheduleId", command.getConcertScheduleId(),
			"sessionId", sessionId
		));

		// 1. NORMAL - 일반적인 예매하기 버튼 입장
		if (command.getEnterType().equals(EnterType.NORMAL)) {

			// 1-1. 이미 대기열에 사용자가 존재하는 경우, 동일한 화면(기기)인지 판단하여 선택지 모달 보여주기 결정
			Double existingScore = redisUtil.zScore(queueKey, user.getId());
			if (existingScore != null) {
				String previousSessionId = redisUtil.hGet(sessionKey, user.getId().toString()); // 대기열을 소유한 화면(기기) 조회
				boolean isSameSession = sessionId.equals(previousSessionId);

				long rank = safeRank(redisUtil.zRank(queueKey, user.getId()));
				long pollingIntervalMs = JitterUtil.nextPollIntervalMillis(rank);

				return EnterDTO.Result.builder()
					.token(token)
					.needToChoose(!isSameSession)	// 다른 화면(기기)이면 선택지 모달을 보여주기 위한 boolean
					.rank(rank)
					.pollingIntervalMs(pollingIntervalMs)
					.build();
			}

			// 1-2. 대기열에 사용자가 없으면 새로 순번 발급 및 등록
			long score = redisUtil.increment(counterKey);
			redisUtil.zAdd(queueKey, user.getId(), score);
			redisUtil.hSet(sessionKey, user.getId().toString(), sessionId); // 대기열을 가져간 화면(기기) 기록

			long rank = safeRank(redisUtil.zRank(queueKey, user.getId()));
			long pollingIntervalMs = JitterUtil.nextPollIntervalMillis(rank);

			return EnterDTO.Result.builder()
				.token(token)
				.needToChoose(false)
				.rank(rank)
				.pollingIntervalMs(pollingIntervalMs)
				.build();
		}

		// 2. RESUME - 기존 순번에 합류
		if (EnterType.RESUME.equals(command.getEnterType())) {
			Long rank = redisUtil.zRank(queueKey, user.getId());

			if (rank == null) {
				throw new GeneralException(QueueErrorCode.NOT_IN_QUEUE); // 이미 처리되었거나 없는 경우
			}

			redisUtil.hSet(sessionKey, user.getId().toString(), sessionId); // 대기열을 새로운 화면(기기)에서 가져갔음을 덮어쓰기

			long pollingIntervalMs = JitterUtil.nextPollIntervalMillis(rank);

			return EnterDTO.Result.builder()
				.token(token)
				.needToChoose(false)
				.rank(rank)
				.pollingIntervalMs(pollingIntervalMs)
				.build();
		}

		// 3. REJOIN - 새롭게 순번 발급
		if (EnterType.REJOIN.equals(command.getEnterType())) {
			long score = redisUtil.increment(counterKey);
			redisUtil.zAdd(queueKey, user.getId(), score); // 순번 덮어쓰기
			redisUtil.hSet(sessionKey, user.getId().toString(), sessionId); // 대기열을 새로운 화면(기기)에서 가져갔음을 덮어쓰기

			long rank = safeRank(redisUtil.zRank(queueKey, user.getId()));
			long pollingIntervalMs = JitterUtil.nextPollIntervalMillis(rank);

			return EnterDTO.Result.builder()
				.token(token)
				.needToChoose(false)
				.rank(rank)
				.pollingIntervalMs(pollingIntervalMs)
				.build();
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

		String queueKey = "queue:concertSchedule:" + concertScheduleId;
		String sessionKey = queueKey + ":session";

		// 하나의 화면에서만 대기열 폴링이 가능하다.
		String registeredSessionId = redisUtil.hGet(sessionKey, user.getId().toString());
		if (!sessionId.equals(registeredSessionId)) {
			throw new GeneralException(QueueErrorCode.DIFFERENT_SESSION);	// 다른 화면(기기)에서 처리 중입니다.
		}

		// TODO: 작업열에 등록되었는지 확인 및 워커 작성
		boolean isActive = false;
		String redirectEndpoint = null;

		if (isActive) {
			return StatusDTO.Result.builder()
				.isActive(true)
				.redirectEndpoint(redirectEndpoint)
				.build();
		}

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
	 * Long → long 변환 시 null로 인한 NPE를 방지합니다.
	 * zADD 직후 곧바로 워커가 처리한 경우를 방지합니다.
	 */
	private long safeRank(Long rank) {
		return rank != null ? rank : 0L;
	}
}
