package ticketing.global.util;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisUtil {

	private final RedisTemplate<String, Object> redisTemplate;

	/**
	 * Sorted Set 추가.
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
	 * Key 삭제
	 */
	public Boolean delete(String key) {
		return redisTemplate.delete(key);
	}

	/**
	 * 값 조회.
	 */
	public Object getValue(String key) {
		return redisTemplate.opsForValue().get(key);
	}

	/**
	 * Sorted Set 멤버 제거.
	 */
	public Long zRemove(String key, Object value) {
		return opsForZSet().remove(key, value);
	}

	/**
	 * RedisTemplate에 SortedSet 초기화.
	 */
	public ZSetOperations<String, Object> opsForZSet() {
		return redisTemplate.opsForZSet();
	}

	/**
	 * Sorted Set 자료형 사이즈.
	 */
	public Long zCard(String key) {
		return opsForZSet().size(key);
	}

	/**
	 * Sorted Set 자료형 start ~ end 까지 조회.
	 */
	public Set<Object> zRange(String key, long start, long end) {
		return opsForZSet().range(key, start, end);
	}

	/**
	 * Sorted Set 자료형 Value의 현재 위치 조회.
	 */
	public Long zRank(String key, Object value) {
		return opsForZSet().rank(key, value);
	}

	/**
	 * Sorted Set 자료형 Value의 score 조회.
	 */
	public Double zScore(String key, Object value) {
		return opsForZSet().score(key, value);
	}

	/**
	 * Hash 필드 값 조회.
	 */
	public String hGet(String key, String hashKey) {
		Object value = redisTemplate.opsForHash().get(key, hashKey);
		return value != null ? value.toString() : null;
	}

	/**
	 * Hash 필드 값 저장.
	 */
	public void hSet(String key, String hashKey, String value) {
		redisTemplate.opsForHash().put(key, hashKey, value);
	}

	/**
	 * Hash 필드 개수 조회.
	 */
	public Long hSize(String key) {
		return redisTemplate.opsForHash().size(key);
	}

	/**
	 * Hash 필드에 TTL을 설정합니다. (HEXPIRE, Redis 7.4+ 필요)
	 */
	public void hExpire(String key, String hashKey, Duration ttl) {
		redisTemplate.opsForHash().expire(key, ttl, List.of(hashKey));
	}

	/**
	 * Sorted Set에서 score가 가장 낮은 멤버부터 최대 count개를 원자적으로 꺼냅니다. (ZPOPMIN)
	 */
	public Set<ZSetOperations.TypedTuple<Object>> zPopMin(String key, long count) {
		return opsForZSet().popMin(key, count);
	}
}