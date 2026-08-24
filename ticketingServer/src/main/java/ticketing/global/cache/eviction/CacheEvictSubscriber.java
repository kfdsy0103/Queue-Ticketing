package ticketing.global.cache.eviction;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.global.cache.enums.CacheGroup;
import ticketing.global.cache.manager.CaffeineCacheStore;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictSubscriber implements MessageListener {

	private final RedisTemplate<String, Object> redisTemplate;
	private final CaffeineCacheStore caffeineCacheStore;
	private final CacheEvictPublisher cacheEvictPublisher;

	@Override
	public void onMessage(Message message, byte[] pattern) {
		try {
			CacheEvictMessage evictMessage = (CacheEvictMessage) redisTemplate.getValueSerializer().deserialize(message.getBody());

			if (evictMessage == null) {
				return;
			}

			// 본인이 발행한 메세지
			if (cacheEvictPublisher.getInstanceId().equals(evictMessage.getInstanceId())) {
				return;
			}

			CacheGroup group = CacheGroup.fromCacheName(evictMessage.getCacheName());
			if (group == null) {
				log.warn("[CacheEvictSubscriber] 알 수 없는 캐시명입니다. cacheName={}", evictMessage.getCacheName());
				return;
			}

			caffeineCacheStore.evict(group, evictMessage.getCacheKey());
			log.debug("[CacheEvictSubscriber] 로컬 캐시를 무효화했습니다. cacheName={}, cacheKey={}", evictMessage.getCacheName(), evictMessage.getCacheKey());
		} catch (Exception e) {
			log.error("[CacheEvictSubscriber] onMessage() 무효화 메시지 처리 중 오류", e);
		}
	}
}
