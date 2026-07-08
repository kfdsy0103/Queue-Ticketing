package ticketing.domain.queue.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.queue.dto.EnterDTO;
import ticketing.domain.queue.enums.EnterType;
import ticketing.domain.queue.exception.QueueErrorCode;
import ticketing.domain.user.entity.User;
import ticketing.domain.user.exception.UserErrorCode;
import ticketing.domain.user.repository.UserRepository;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.util.JitterUtil;
import ticketing.global.util.RedisUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

	private final UserRepository userRepository;
	private final RedisUtil redisUtil;

	/**
	 * 대기열 입장을 처리합니다.
	 * 		enter() 유형은 다음 3가지로 분류됩니다.
	 * 			1. NORMAL : 일반적인 '예매하기' 버튼을 눌러 대기열에 입장하는 경우
	 * 			2. RESUME : 다른 브라우저에서 이미 순번을 받아놨고, 선택창에서 '아니오, 기존 예매를 유지합니다.' 를 클릭한 경우
	 * 			3. REJOIN : 다른 브라우저에서 이미 순번을 받아놨고, 선택창에서 '네, 새로운 예매를 진행합니다.' 를 클릭한 경우
	 */
	public EnterDTO.Result enter(EnterDTO.Command command) {

		User user = userRepository.findById(command.getUserId())
			.orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

		String queueKey = "queue:concertSchedule:" + command.getConcertScheduleId();
		String counterKey = queueKey + ":counter";

		// 1. NORMAL
		if (command.getEnterType().equals(EnterType.NORMAL)) {

			Long score = redisUtil.increment(counterKey);
			boolean isEnqueued = redisUtil.zAddNX(queueKey, user.getId(), score);

			Long rank = redisUtil.zRank(queueKey, user.getId());
			Long pollingIntervalMs = JitterUtil.nextPollIntervalMillis(rank);

			if (isEnqueued) {
				return EnterDTO.Result.builder()
					.token(user.getId().toString())
					.needToChoose(false)
					.rank(rank)
					.pollingInternalMs(pollingIntervalMs)
					.build();
			}
			else {
				return EnterDTO.Result.builder()
					.token(user.getId().toString())
					.needToChoose(true)	// 선택 필요
					.rank(rank)
					.pollingInternalMs(pollingIntervalMs)
					.build();
			}
		}

		// 2. RESUME - 기존 순번에 합류
		if (command.getEnterType().equals(EnterType.RESUME)) {
			Long rank = redisUtil.zRank(queueKey, user.getId());

			if (rank == null) {
				throw new GeneralException(QueueErrorCode.NOT_IN_QUEUE);	// 이미 처리되거나 없는 경우
			}

			return EnterDTO.Result.builder()
				.token(user.getId().toString())
				.needToChoose(false)
				.rank(rank)
				.pollingInternalMs(JitterUtil.nextPollIntervalMillis(rank))
				.build();
		}

		// 3. REJOIN - 새롭게 순번 발급
		if (command.getEnterType().equals(EnterType.REJOIN)) {
			Long score = redisUtil.increment(counterKey);
			redisUtil.zAdd(queueKey, user.getId(), score);	// Add로 해야 덮어쓰기
			Long rank = redisUtil.zRank(queueKey, user.getId());
			Long pollingIntervalMs = JitterUtil.nextPollIntervalMillis(rank);

			return EnterDTO.Result.builder()
				.token(user.getId().toString())
				.needToChoose(false)
				.rank(rank)
				.pollingInternalMs(pollingIntervalMs)
				.build();
		}

		throw new GeneralException(QueueErrorCode.INVALID_ENTER_TYPE);
	}
}
