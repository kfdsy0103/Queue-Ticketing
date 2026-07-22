package ticketing.global.util;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisUtil {

	private final RedisTemplate<String, Object> redisTemplate;

	/**
	 * Sorted Set에 값을 추가합니다. 이미 존재하면 score를 덮어씁니다. (ZADD)
	 */
	public Boolean zAdd(String key, Object value, double score) {
		return opsForZSet().add(key, value, score);
	}

	/**
	 * 카운터 변수 원자적 증가 (INCR).
	 */
	public Long increment(String key) {
		return redisTemplate.opsForValue().increment(key);
	}

	/**
	 * RedisTemplate에 SortedSet 초기화.
	 */
	public ZSetOperations<String, Object> opsForZSet() {
		return redisTemplate.opsForZSet();
	}

	/**
	 * Sorted Set 자료형 Value의 현재 위치 조회.
	 */
	public Long zRank(String key, Object value) {
		return opsForZSet().rank(key, value);
	}

	/**
	 * String 값 조회. (GET)
	 */
	public String get(String key) {
		Object value = redisTemplate.opsForValue().get(key);
		return value != null ? value.toString() : null;
	}

	/**
	 * String 값을 TTL과 함께 저장합니다. (SET + EX)
	 */
	public void set(String key, String value, Duration ttl) {
		redisTemplate.opsForValue().set(key, value, ttl);
	}

	/**
	 * Key를 삭제합니다. (DEL)
	 */
	public void delete(String key) {
		redisTemplate.delete(key);
	}

	/**
	 * Lua 스크립트를 원자적으로 실행합니다.
	 */
	public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
		return redisTemplate.execute(script, keys, args);
	}

	/**
	 * 이미 존재하는 Key의 TTL만 갱신합니다. (EXPIRE) 값은 그대로 유지됩니다.
	 */
	public void expire(String key, Duration ttl) {
		redisTemplate.expire(key, ttl);
	}

	/**
	 * Key의 존재 여부를 확인합니다. (EXISTS)
	 */
	public boolean hasKey(String key) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(key));
	}

	/**
	 * 여러 Key의 값을 한 번의 요청으로 조회합니다. (MGET)
	 */
	public List<Object> multiGet(List<String> keys) {
		return redisTemplate.opsForValue().multiGet(keys);
	}
}