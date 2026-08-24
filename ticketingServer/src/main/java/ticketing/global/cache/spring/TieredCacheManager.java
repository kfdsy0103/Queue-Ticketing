package ticketing.global.cache.spring;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import ticketing.global.cache.enums.CacheGroup;
import ticketing.global.cache.enums.CacheType;
import ticketing.global.cache.eviction.CacheEvictPublisher;
import ticketing.global.cache.manager.CacheStore;
import ticketing.global.cache.service.SingleFlightExecutor;
import ticketing.global.util.RedisLockService;

/**
 * org.springframework.cache.CacheManager 구현체. CacheGroup별로 TieredCache를 생성해 보관한다.
 * 이 빈이 컨텍스트 내 유일한 CacheManager 빈이 되어야 스프링 부트의 기본 캐시 오토컨피그가 비활성화된다.
 */
@Component
public class TieredCacheManager implements org.springframework.cache.CacheManager {

	private final Map<String, TieredCache> caches;

	public TieredCacheManager(
		MeterRegistry meterRegistry,
		RedisLockService redisLockService,
		List<CacheStore> cacheStores,
		CacheEvictPublisher cacheEvictPublisher,
		@Qualifier("cacheRefreshExecutor") Executor cacheRefreshExecutor,
		SingleFlightExecutor singleFlightExecutor
	) {
		Map<CacheType, CacheStore> storesByType = cacheStores.stream()
			.collect(Collectors.toUnmodifiableMap(CacheStore::getSupportedType, Function.identity()));

		CacheStore localStore = storesByType.get(CacheType.LOCAL);
		CacheStore globalStore = storesByType.get(CacheType.GLOBAL);

		this.caches = Arrays.stream(CacheGroup.values())
			.collect(Collectors.toUnmodifiableMap(
				CacheGroup::getCacheName,
				group -> new TieredCache(
					group,
					localStore,
					globalStore,
					redisLockService,
					singleFlightExecutor,
					cacheEvictPublisher,
					cacheRefreshExecutor,
					meterRegistry
				)
			));
	}

	@Override
	public org.springframework.cache.Cache getCache(String name) {
		return caches.get(name);
	}

	@Override
	public Collection<String> getCacheNames() {
		return caches.keySet();
	}
}
