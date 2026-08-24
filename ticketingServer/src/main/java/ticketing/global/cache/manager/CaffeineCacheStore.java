package ticketing.global.cache.manager;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.cache.dto.CacheEntry;
import ticketing.global.cache.exception.CacheErrorCode;
import ticketing.global.cache.enums.CacheGroup;
import ticketing.global.cache.enums.CacheType;

@Slf4j
@Component
public class CaffeineCacheStore implements CacheStore {

	private final Map<String, Cache<String, CacheEntry<?>>> caches = new HashMap<>();

	public CaffeineCacheStore() {
		// Local을 사용하는 캐시이면 해당 그룹을 카페인에 등록
		for (CacheGroup group : CacheGroup.values()) {
			if (group.useLocalCache()) {
				caches.put(
					group.getCacheName(),
					Caffeine.newBuilder()
						.expireAfterWrite(group.getExpiredAfterWriteLocal())
						.maximumSize(group.getMaximumSize())
						.build()
				);
			}
		}

		log.info("[CaffeineCacheStore] 로컬 캐시 초기화 완료. groups={}", this.caches.keySet());
	}

	@Override
	public CacheType getSupportedType() {
		return CacheType.LOCAL;
	}

	@Override
	public <T> CacheEntry<T> get(CacheGroup group, String cacheKey) {
		Cache<String, CacheEntry<?>> cache = caches.get(group.getCacheName());
		if (cache == null) {
			log.error("[CaffeineCacheStore] 로컬 캐시를 쓰지 않는 캐시명입니다. cacheName={}", group.getCacheName());
			throw new GeneralException(CacheErrorCode.LOCAL_CACHE_NOT_FOUND);
		}
		return (CacheEntry<T>) cache.getIfPresent(cacheKey);
	}

	@Override
	public void set(CacheGroup group, String cacheKey, CacheEntry<?> value) {
		Cache<String, CacheEntry<?>> cache = caches.get(group.getCacheName());
		if (cache != null) {
			cache.put(cacheKey, value);
		}
	}

	@Override
	public Long getExpire(CacheGroup group, String cacheKey) {

		Cache<String, CacheEntry<?>> cache = caches.get(group.getCacheName());
		if (cache == null) {
			return null;
		}

		Duration age = cache.policy().expireAfterWrite()
			.flatMap(policy -> policy.ageOf(cacheKey))
			.orElse(null);
		if (age == null) {
			return null;
		}

		// 설정한 TTL 정책 값에서 age 뺀 값으로 구함
		long remainingMillis = group.getExpiredAfterWriteLocal().minus(age).toMillis();

		return Math.max(remainingMillis, 0L);
	}

	@Override
	public void evict(CacheGroup group, String cacheKey) {
		String cacheName = group.getCacheName();

		Cache<String, CacheEntry<?>> cache = caches.get(cacheName);
		if (cache == null) {
			log.warn("[CaffeineCacheStore] 로컬 캐시를 쓰지 않는 캐시명입니다. cacheName={}", cacheName);
			return;
		}

		cache.invalidate(cacheKey);
	}

	/**
	 * 캐시 그룹 하나를 모두 비웁니다.
	 */
	public void clear(CacheGroup group) {
		Cache<String, CacheEntry<?>> cache = caches.get(group.getCacheName());
		if (cache != null) {
			cache.invalidateAll();
		}
	}
}
