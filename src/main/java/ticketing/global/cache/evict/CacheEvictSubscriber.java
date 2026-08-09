package ticketing.global.cache.evict;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.global.util.LocalCacheStore;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictSubscriber implements MessageListener {

	private final RedisTemplate<String, Object> redisTemplate;
	private final LocalCacheStore localCacheStore;

	@Override
	public void onMessage(Message message, byte[] pattern) {
		try {
			CacheEvictMessage evictMessage = (CacheEvictMessage) redisTemplate.getValueSerializer().deserialize(message.getBody());

			if (evictMessage == null) {
				return;
			}

			localCacheStore.evict(evictMessage.getCacheName(), evictMessage.getCacheKey());
			log.debug("[CacheEvictSubscriber] 로컬 캐시를 무효화했습니다. cacheKey={}", evictMessage.getCacheKey());
		} catch (Exception e) {
			log.error("[CacheEvictSubscriber] onMessage() 무효화 메시지 처리 중 오류", e);
		}
	}
}
