package ticketing.global.cache.spring;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import ticketing.global.cache.constants.CacheMetric;
import ticketing.global.cache.dto.CacheEntry;
import ticketing.global.cache.dto.CacheResult;
import ticketing.global.cache.enums.CacheGroup;
import ticketing.global.cache.enums.CacheType;
import ticketing.global.cache.eviction.CacheEvictPublisher;
import ticketing.global.cache.manager.CacheStore;
import ticketing.global.cache.manager.CaffeineCacheStore;
import ticketing.global.cache.service.SingleFlightExecutor;
import ticketing.global.util.RedisLockService;

/**
 * 스프링 Cache SPI 구현체.
 * CacheGroup 하나당 인스턴스 하나씩 생성되어 PER(확률적 조기 재계산),
 * SingleFlight/분산락 기반 스탬피드 방어, L1(Caffeine)+L2(Redis) 합성 캐시,
 * Pub/Sub 무효화를 @Cacheable이 호출하는 get(key, Callable) 훅 안에서 그대로 수행한다.
 * (기존 CacheService의 오케스트레이션 로직을 이곳으로 이식)
 */
@Slf4j
public class TieredCache implements Cache {

	// 튜닝하는 값, (beta가 높을 수록 or TTL과 timeToCompute와 차이가 크지 않을 수록 자주 갱신)
	private static final double BETA = 1.0;

	private static final String CACHE_PREFIX = "cache:";
	private static final Duration LOCK_WAIT = Duration.ofMillis(2000L);
	private static final Duration LOCK_LEASE = Duration.ofMillis(5000L);

	private final CacheGroup group;
	private final CacheStore localManager;
	private final CacheStore globalManager;
	private final RedisLockService redisLockService;
	private final SingleFlightExecutor singleFlightExecutor;
	private final CacheEvictPublisher cacheEvictPublisher;
	private final Executor cacheRefreshExecutor;
	private final MeterRegistry meterRegistry;
	private final Set<String> inFlightKeys = ConcurrentHashMap.newKeySet();

	public TieredCache(
		CacheGroup group,
		CacheStore localManager,
		CacheStore globalManager,
		RedisLockService redisLockService,
		SingleFlightExecutor singleFlightExecutor,
		CacheEvictPublisher cacheEvictPublisher,
		Executor cacheRefreshExecutor,
		MeterRegistry meterRegistry
	) {
		this.group = group;
		this.localManager = localManager;
		this.globalManager = globalManager;
		this.redisLockService = redisLockService;
		this.singleFlightExecutor = singleFlightExecutor;
		this.cacheEvictPublisher = cacheEvictPublisher;
		this.cacheRefreshExecutor = cacheRefreshExecutor;
		this.meterRegistry = meterRegistry;
	}

	@Override
	public String getName() {
		return group.getCacheName();
	}

	@Override
	public Object getNativeCache() {
		return this;
	}

	@Override
	public ValueWrapper get(Object key) {
		CacheEntry<Object> entry = lookup(getCacheKey(key));
		return (entry != null) ? new SimpleValueWrapper(entry.getCachedData()) : null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T get(Object key, Class<T> type) {
		ValueWrapper wrapper = get(key);
		if (wrapper == null) {
			return null;
		}
		Object value = wrapper.get();
		if (type != null && value != null && !type.isInstance(value)) {
			throw new IllegalStateException("캐시된 값이 요청한 타입과 일치하지 않습니다 [" + type.getName() + "]: " + value);
		}
		return (type != null) ? type.cast(value) : (T) value;
	}

	@Override
	public <T> T get(Object key, Callable<T> valueLoader) {

		String cacheKey = getCacheKey(key);
		CacheType cacheType = group.getCacheType();

		CacheResult<T> result;
		if (cacheType == CacheType.LOCAL) {
			result = getFromLocal(cacheKey, valueLoader);
		} else if (cacheType == CacheType.GLOBAL) {
			result = getFromGlobal(cacheKey, valueLoader);
		} else {
			result = getFromComposite(cacheKey, valueLoader);
		}

		recordCacheMetric(result.getMetricResult());

		CacheEntry<T> entry = result.getEntry();
		return (entry != null) ? entry.getCachedData() : null;
	}

	@Override
	public void put(Object key, Object value) {
		String cacheKey = getCacheKey(key);
		CacheEntry<Object> entry = new CacheEntry<>(value, 0L);

		if (group.getCacheType() == CacheType.LOCAL) {
			saveIntoLocal(cacheKey, entry);
		} else {
			saveIntoGlobal(cacheKey, entry);	// COMPOSITE이면 saveIntoGlobal이 로컬 동기화 + Pub/Sub까지 함께 처리한다
		}
	}

	@Override
	public void evict(Object key) {
		String cacheKey = getCacheKey(key);
		CacheType cacheType = group.getCacheType();

		if (cacheType != CacheType.GLOBAL) {
			localManager.evict(group, cacheKey);
		}
		if (cacheType != CacheType.LOCAL) {
			globalManager.evict(group, cacheKey);
		}
		if (cacheType == CacheType.COMPOSITE) {
			cacheEvictPublisher.publish(group.getCacheName(), cacheKey);
		}
	}

	@Override
	public boolean evictIfPresent(Object key) {
		evict(key);
		return true;
	}

	@Override
	public void clear() {
		if (localManager instanceof CaffeineCacheStore caffeineCacheStore) {
			caffeineCacheStore.clear(group);
		}
		log.warn("[TieredCache] clear()는 L1(로컬)만 비웁니다. L2(글로벌) 전체 삭제는 지원하지 않습니다 - cacheName={}", group.getCacheName());
	}

	/**
	 * 로컬 <-> DB, PER + SingleFlight
	 */
	private <T> CacheResult<T> getFromLocal(String cacheKey, Callable<T> valueLoader) {

		CacheEntry<T> cached = localManager.get(group, cacheKey);
		Long remainingTTL = (cached != null) ? localManager.getExpire(group, cacheKey) : null;

		if (cached == null || remainingTTL == null || remainingTTL <= 0) {
			return singleFlightExecutor.execute(cacheKey, () -> {
				CacheEntry<T> refreshed = localManager.get(group, cacheKey);
				if (refreshed != null) {
					return new CacheResult<>(refreshed, CacheMetric.RESULT_LOCAL_HIT);
				}

				CacheEntry<T> entry = loadFromValueLoader(cacheKey, valueLoader);
				saveIntoLocal(cacheKey, entry);

				return new CacheResult<>(entry, CacheMetric.RESULT_MISS);
			});
		}

		if (shouldRecompute(cached.getTimeToCompute(), remainingTTL)) {
			log.info("PER에 의해 백그라운드에서 로컬 캐시를 갱신합니다 - key: {}, remainingTTL: {}", cacheKey, remainingTTL);
			submitRefreshTaskInBackground(cacheKey, () -> {
				CacheEntry<T> entry = loadFromValueLoader(cacheKey, valueLoader);
				saveIntoLocal(cacheKey, entry);
			});
		}

		return new CacheResult<>(cached, CacheMetric.RESULT_LOCAL_HIT);
	}

	/**
	 * 글로벌 <-> DB, PER + 분산락으로 스템피드 방어
	 */
	private <T> CacheResult<T> getFromGlobal(String cacheKey, Callable<T> valueLoader) {

		CacheEntry<T> cached = globalManager.get(group, cacheKey);
		Long remainingTTL = (cached != null) ? globalManager.getExpire(group, cacheKey) : null;

		if (cached == null || remainingTTL == null || remainingTTL <= 0) {

			boolean acquired = redisLockService.tryLock(cacheKey, LOCK_LEASE, LOCK_WAIT);

			try {
				CacheEntry<T> refreshed = globalManager.get(group, cacheKey);
				if (refreshed != null) {
					return new CacheResult<>(refreshed, CacheMetric.RESULT_GLOBAL_HIT);
				}

				if (!acquired) {
					log.warn("캐시 갱신 락 획득에 실패했지만 캐시가 비어 있어 DB로 조회합니다 - key: {}", cacheKey);
				}

				CacheEntry<T> entry = loadFromValueLoader(cacheKey, valueLoader);
				saveIntoGlobal(cacheKey, entry);

				return new CacheResult<>(entry, CacheMetric.RESULT_MISS);
			} finally {
				if (acquired) {
					redisLockService.releaseLock(cacheKey);
				}
			}
		}

		if (shouldRecompute(cached.getTimeToCompute(), remainingTTL)) {
			log.info("PER에 의해 백그라운드에서 글로벌 캐시를 갱신합니다 - key: {}, remainingTTL: {}", cacheKey, remainingTTL);

			submitRefreshTaskInBackground(cacheKey, () -> {
				if (!redisLockService.tryLock(cacheKey, LOCK_LEASE)) {
					return;
				}

				try {
					CacheEntry<T> entry = loadFromValueLoader(cacheKey, valueLoader);
					saveIntoGlobal(cacheKey, entry);
				} finally {
					redisLockService.releaseLock(cacheKey);
				}
			});
		}

		return new CacheResult<>(cached, CacheMetric.RESULT_GLOBAL_HIT);
	}

	/**
	 * 로컬 캐시 <-> 글로벌 캐시 <-> DB,
	 * 		로컬 -> 글로벌은 SingleFlight로 하나의 스레드만 레디스 조회하도록 방어
	 * 		글로벌 -> DB는 분산락으로 방어
	 */
	private <T> CacheResult<T> getFromComposite(String cacheKey, Callable<T> valueLoader) {

		CacheEntry<T> localCacheEntry = localManager.get(group, cacheKey);
		if (localCacheEntry != null) {
			return new CacheResult<>(localCacheEntry, CacheMetric.RESULT_LOCAL_HIT);
		}

		return singleFlightExecutor.execute(cacheKey, () -> {

			CacheEntry<T> refreshCacheEntry = localManager.get(group, cacheKey);
			if (refreshCacheEntry != null) {
				return new CacheResult<>(refreshCacheEntry, CacheMetric.RESULT_LOCAL_HIT);
			}

			CacheResult<T> result = getFromGlobal(cacheKey, valueLoader);
			saveIntoLocal(cacheKey, result.getEntry());

			return result;
		});
	}

	private <T> void saveIntoLocal(String cacheKey, CacheEntry<T> entry) {
		if (entry == null) {
			return;
		}
		localManager.set(group, cacheKey, entry);
	}

	private <T> void saveIntoGlobal(String cacheKey, CacheEntry<T> entry) {
		if (entry == null) {
			return;
		}

		globalManager.set(group, cacheKey, entry);

		if (group.getCacheType() == CacheType.COMPOSITE) {
			localManager.set(group, cacheKey, entry);
			cacheEvictPublisher.publish(group.getCacheName(), cacheKey);
		}
	}

	/**
	 * valueLoader를 호출해 PER에 필요한 timeToCompute도 함께 계산한다.
	 * valueLoader가 예외를 던지면 Cache SPI 규약대로 ValueRetrievalException으로 감싸서 던진다 (캐시에는 저장하지 않음).
	 */
	private <T> CacheEntry<T> loadFromValueLoader(Object key, Callable<T> valueLoader) {
		long startMs = System.currentTimeMillis();
		T newValue;
		try {
			newValue = valueLoader.call();
		} catch (Exception e) {
			throw new ValueRetrievalException(key, valueLoader, e);
		}
		long elapsedMs = System.currentTimeMillis() - startMs;

		if (newValue == null) {
			return null;
		}

		return new CacheEntry<>(newValue, elapsedMs);
	}

	/**
	 * timeToCompute * beta * -log(rand()) > remainingTTL 이면 갱신 채택
	 */
	private boolean shouldRecompute(long timeToCompute, long remainingTTL) {
		double randomGap = ThreadLocalRandom.current().nextDouble();
		if (randomGap == 0.0) randomGap = 0.000001;
		double gapScore = timeToCompute * BETA * -Math.log(randomGap);

		return gapScore > remainingTTL;
	}

	/**
	 * 캐시 refresh Task를 비동기적으로 백그라운드에서 처리합니다.
	 * putIfAbsent로 같은 키가 이미 제출되어 있으면 제출하지 않아 불필요한 제출을 막습니다.
	 */
	private void submitRefreshTaskInBackground(String cacheKey, Runnable refreshTask) {
		if (!inFlightKeys.add(cacheKey)) {
			return;
		}

		try {
			cacheRefreshExecutor.execute(() -> {
				try {
					refreshTask.run();
				} catch (Exception e) {
					log.warn("백그라운드 캐시 갱신에 실패했습니다 - key: {}", cacheKey, e);
				} finally {
					inFlightKeys.remove(cacheKey);
				}
			});
		} catch (Exception e) {
			inFlightKeys.remove(cacheKey);
		}
	}

	/**
	 * 로더/PER 트리거 없이 현재 캐시 상태만 조회한다 (get(Object key)에서 사용).
	 */
	private CacheEntry<Object> lookup(String cacheKey) {
		CacheType cacheType = group.getCacheType();

		if (cacheType == CacheType.LOCAL) {
			return localManager.get(group, cacheKey);
		}
		if (cacheType == CacheType.GLOBAL) {
			return globalManager.get(group, cacheKey);
		}

		CacheEntry<Object> local = localManager.get(group, cacheKey);
		return (local != null) ? local : globalManager.get(group, cacheKey);
	}

	private String getCacheKey(Object key) {
		return CACHE_PREFIX + group.getCacheName() + ":" + key;
	}

	private void recordCacheMetric(String result) {
		meterRegistry.counter(
			CacheMetric.CACHE_GETS_METRIC,
			CacheMetric.TAG_GROUP, group.getCacheName(),
			CacheMetric.TAG_RESULT, result
		).increment();
	}
}
