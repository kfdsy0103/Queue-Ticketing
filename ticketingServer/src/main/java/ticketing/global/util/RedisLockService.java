package ticketing.global.util;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisLockService {

	private static final String LOCK_PREFIX = "lock:";

	private final RedissonClient redissonClient;

	/**
	 * 대기 없이 락을 시도합니다.
	 */
	public boolean tryLock(String key, Duration lease) {
		try {
			RLock lock = redissonClient.getLock(getLockKey(key));
			return lock.tryLock(0, lease.toMillis(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error("[RedisLockService] tryLock() 인터럽트 에러 발생, key: {}", key);
			return false;
		}
	}

	/**
	 * 대기 시간동안 락을 시도합니다.
	 */
	public boolean tryLock(String key, Duration lease, Duration waitFor) {
		try {
			RLock lock = redissonClient.getLock(getLockKey(key));
			return lock.tryLock(waitFor.toMillis(), lease.toMillis(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error("[RedisLockService] tryLock() 인터럽트 에러 발생, key: {}", key);
			return false;
		}
	}

	/**
	 * 락을 해제합니다.
	 */
	public void releaseLock(String key) {
		RLock lock = redissonClient.getLock(getLockKey(key));
		if (lock.isHeldByCurrentThread()) {
			lock.unlock();
		}
	}

	/**
	 * 락 키에는 접두어를 붙여 다른 용도의 키와 섞이지 않도록 합니다.
	 */
	private String getLockKey(String key) {
		return LOCK_PREFIX + key;
	}
}
