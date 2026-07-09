package ticketing.domain.queue.service;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.queue.dto.EnterDTO;
import ticketing.domain.queue.exception.QueueErrorCode;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueFacadeService {

	private static final long LOCK_WAIT_SECONDS = 2;
	private static final long LOCK_LEASE_SECONDS = 3;

	private final QueueService queueService;
	private final RedissonClient redissonClient;

	/**
	 * 사용자 단위 락을 잡은 상태에서 대기열 입장 로직을 실행합니다.
	 */
	public EnterDTO.Result enter(EnterDTO.Command command) {

		String lockKey = "lock:queue:concertSchedule:" + command.getConcertScheduleId() + ":user:" + command.getUserId();
		RLock lock = redissonClient.getLock(lockKey);
		boolean acquired = false;

		try {
			// 멱등 처리 락 획득
			acquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
			if (!acquired) {
				throw new GeneralException(QueueErrorCode.QUEUE_BUSY);
			}

			// enter() 실제 로직 처리
			return queueService.enter(command);

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new GeneralException(QueueErrorCode.QUEUE_BUSY);
		} finally {
			if (acquired && lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}
}
