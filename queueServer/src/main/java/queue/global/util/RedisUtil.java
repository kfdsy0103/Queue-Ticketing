package queue.global.util;

import java.time.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisUtil {

	private final RedisTemplate<String, Object> redisTemplate;

	/**
	 * Sorted Set에 값을 추가합니다. 이미 존재하면 무시합니다. (ZADD NX)
	 */
	public Boolean zAddIfAbsent(String key, Object value, double score) {
		return opsForZSet().addIfAbsent(key, value, score);
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
}
