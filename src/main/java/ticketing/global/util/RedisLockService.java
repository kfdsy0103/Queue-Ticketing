package ticketing.global.util;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisLockService {

	private final StringRedisTemplate redisTemplate;

	public RedisLockService(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	/**
	 * key에 value를 설정해 락을 시도합니다. (SET NX, ttl 동안만 유지)
	 */
	public boolean tryLock(String key, String value, Duration ttl) {
		Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
		return Boolean.TRUE.equals(acquired);
	}

	/**
	 * 락을 해제합니다.
	 */
	public void releaseLock(String key) {
		redisTemplate.delete(key);
	}
}
