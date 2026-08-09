package ticketing.global.cache.evict;

import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.global.cache.constants.CacheChannel;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictPublisher {

	private final String instanceId = UUID.randomUUID().toString();

	private final RedisTemplate<String, Object> redisTemplate;

	public String getInstanceId() {
		return instanceId;
	}

	@Retryable(
		include = Exception.class,
		recover = "recover",
		maxAttempts = 3,
		backoff = @Backoff(delay = 100)
	)
	public void publish(String cacheName, String cacheKey) {
		redisTemplate.convertAndSend(
			CacheChannel.CACHE_EVICT,
			new CacheEvictMessage(cacheName, cacheKey, instanceId)
		);
		log.debug("[CacheEvictPublisher] 로컬 캐시 무효화 발행. cacheKey={}", cacheKey);
	}

	@Recover
	public void recover(Exception e, String cacheName, String cacheKey) {
		log.error("[CacheEvictPublisher] 로컬 캐시 무효화 발행에 실패했습니다. cacheName={}, cacheKey={}", cacheName, cacheKey, e);
	}
}
