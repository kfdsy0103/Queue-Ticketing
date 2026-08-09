package ticketing.global.cache;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import ticketing.global.cache.enums.CacheGroup;
import ticketing.global.cache.enums.CacheType;
import ticketing.global.cache.evict.CacheEvictPublisher;
import ticketing.global.cache.manager.CacheManager;
import ticketing.global.util.RedisLockService;

/**
 * 캐시 계층을 오케스트레이션하는 진입점.
 * 저장소 접근은 CacheManager 구현체에 맡기고, 여기서는 갱신 정책만 다룹니다.
 *
 * timeToCompute * beta * -log(rand()) > remainingTTL
 * 		1. 캐시 조회
 * 		2. 캐시 생성 시간과 beta(가중치) 부여한 뒤 랜덤한 x값 생성
 * 		3. x 값과 remainingTTL을 비교하여 캐시 갱신 여부 결정
 *
 * 		1. Hit + PER X -> return cache;
 * 		2. Hit + PER O -> 백그라운드 갱신(블로킹 안하기 위해) + return cache;
 *  	3. Miss -> 분산락 + 더블 체크로 스템피드 방어
 *
 *  PER은 미스했을 때 DB로 내려가는 계층에 건다.
 *  	1. LOCAL의 로컬 캐시     -> 미스하면 DB로 가므로 PER o (인스턴스별 캐시라 분산락은 x)
 *  	2. GLOBAL의 글로벌 캐시  -> 미스하면 DB로 가므로 PER o + 분산락 o
 *  	3. COMPOSITE의 로컬 캐시 -> 미스해도 글로벌 캐시로 갈 뿐이라 PER x
 *
 *  백그라운드 갱신 시 Task를 여러 번 제출되는 문제
 *  	1. JVM 내에서 Task는 한번만 제출될 수 있도록 원자적 add 지원하는 자료구조 사용
 *  	2. 분산 EC2 사이에서는 제출된 Task가 여러 개이므로, 하나만 DB 다녀올 수 있도록 분산락
 *
 *  CacheType에 따라 타는 계층이 달라진다.
 *  	1. LOCAL     -> 로컬 캐시 -> DB
 *  	2. GLOBAL    -> 글로벌 캐시 -> DB
 *  	3. COMPOSITE -> 로컬 캐시 -> 글로벌 캐시 -> DB
 */
@Slf4j
@Service
public class CacheService {

	// 만료 시간이 가까울수록 재계산 확률 증가 (beta가 높을 수록 or TTL과 timeToCompute와 차이가 크지 않을 수록 자주 갱신)
	// beta = 튜닝하는 값
	private static final double BETA = 1.0;

	private static final String CACHE_PREFIX = "cache:";
	private static final Duration LOCK_WAIT = Duration.ofMillis(2000L);
	private static final Duration LOCK_LEASE = Duration.ofMillis(5000L);

	// 캐시 히트율 집계용 메트릭
	private static final String CACHE_GETS_METRIC = "ticketing.cache.gets";
	private static final String TAG_GROUP = "group";
	private static final String TAG_RESULT = "result";

	private static final String RESULT_LOCAL_HIT = "local_hit";
	private static final String RESULT_GLOBAL_HIT = "global_hit";
	private static final String RESULT_MISS = "miss";

	// PER ok -> 백그라운드 갱신을 요청할 때, 여러 스레드의 중복 요청을 막기 위함
	private final Set<String> inFlightKeys = ConcurrentHashMap.newKeySet();	// 락 범위가 key 단위인 자료구조

	private final Map<CacheType, CacheManager> cacheManagers;
	private final RedisLockService redisLockService;
	private final Executor cacheRefreshExecutor;
	private final MeterRegistry meterRegistry;
	private final CacheEvictPublisher cacheEvictPublisher;

	public CacheService(
		List<CacheManager> cacheManagers,
		RedisLockService redisLockService,
		@Qualifier("cacheRefreshExecutor") Executor cacheRefreshExecutor,
		MeterRegistry meterRegistry,
		CacheEvictPublisher cacheEvictPublisher
	) {
		this.cacheManagers = cacheManagers.stream()
			.collect(Collectors.toUnmodifiableMap(CacheManager::getSupportedType, Function.identity()));
		this.redisLockService = redisLockService;
		this.cacheRefreshExecutor = cacheRefreshExecutor;
		this.meterRegistry = meterRegistry;
		this.cacheEvictPublisher = cacheEvictPublisher;
	}

	/**
	 * 캐시 조회. 앞 계층이 미스면 뒤 계층으로, 마지막까지 미스면 DB로 내려갑니다.
	 */
	public <T> T getCacheWithPER(CacheGroup group, String key, Supplier<T> dbQuery) {

		String cacheKey = getCacheKey(group, key);

		return switch (group.getCacheType()) {
			case LOCAL -> unwrap(getFromLocal(group, cacheKey, dbQuery));
			case GLOBAL -> unwrap(getFromGlobal(group, cacheKey, dbQuery));
			case COMPOSITE -> unwrap(getFromComposite(group, cacheKey, dbQuery));
		};
	}

	/**
	 * 캐시를 즉시 무효화합니다. 쓰는 계층을 모두 지우고, 로컬 캐시는 다른 인스턴스도 함께 버리도록 알립니다.
	 */
	public void evict(CacheGroup group, String key) {

		String cacheKey = getCacheKey(group, key);
		CacheType cacheType = group.getCacheType();

		if (cacheType != CacheType.LOCAL) {
			manager(CacheType.GLOBAL).evict(group, cacheKey);
		}

		if (cacheType != CacheType.GLOBAL) {
			manager(CacheType.LOCAL).evict(group, cacheKey);
			cacheEvictPublisher.publish(group.getCacheName(), cacheKey);
		}
	}

	/**
	 * 로컬 히트면 Redis까지 가지 않고, 미스면 글로벌 캐시에서 받아온 값을 로컬에도 채워둡니다.
	 */
	private <T> CacheWrapper<T> getFromComposite(CacheGroup group, String cacheKey, Supplier<T> dbQuery) {

		CacheManager local = manager(CacheType.LOCAL);

		// 1. 로컬 히트 - 다음 계층으로 내려가지 않는다
		CacheWrapper<T> localCached = local.get(group, cacheKey);
		if (localCached != null) {
			recordCacheResult(group, RESULT_LOCAL_HIT);
			return localCached;
		}

		// 2. 로컬 미스 - 글로벌 캐시에서 가져온 값을 로컬에도 채워둔다
		CacheWrapper<T> wrapper = getFromGlobal(group, cacheKey, dbQuery);
		if (wrapper != null) {
			local.set(group, cacheKey, wrapper);
		}

		return wrapper;
	}

	/**
	 * PER 기반 로컬 캐시 조회.
	 * 로컬 캐시만 쓰는 그룹은 미스하면 곧바로 DB로 내려가므로 글로벌과 동일하게 만료 직전 미리 갱신합니다.
	 * 다만 로컬 캐시는 인스턴스끼리 공유하지 않아, 분산락으로 묶어봐야 갱신 결과를 나눠 가질 수 없습니다.
	 */
	private <T> CacheWrapper<T> getFromLocal(CacheGroup group, String cacheKey, Supplier<T> dbQuery) {

		CacheManager local = manager(CacheType.LOCAL);
		CacheWrapper<T> cached = local.get(group, cacheKey);

		// 1. Miss - DB 조회
		if (cached == null) {
			log.debug("로컬 캐시 미스 - key: {}", cacheKey);
			recordCacheResult(group, RESULT_MISS);
			return refreshLocalCache(group, cacheKey, dbQuery);
		}

		Long remainingTTL = local.getExpire(group, cacheKey);
		if (remainingTTL == null || remainingTTL <= 0) {
			recordCacheResult(group, RESULT_MISS);
			return refreshLocalCache(group, cacheKey, dbQuery);
		}

		recordCacheResult(group, RESULT_LOCAL_HIT);

		// 2. Hit + PER o
		if (isPerTriggered(cached.getTimeToCompute(), remainingTTL)) {
			log.info("PER에 의해 백그라운드에서 로컬 캐시를 갱신합니다 - key: {}, remainingTTL: {}", cacheKey, remainingTTL);
			submitRefreshTaskInBackground(cacheKey, () -> refreshLocalInBackground(group, cacheKey, dbQuery));
		}

		// 3. Hit + PER x
		return cached;
	}

	/**
	 * PER 기반 글로벌 캐시 조회
	 */
	private <T> CacheWrapper<T> getFromGlobal(CacheGroup group, String cacheKey, Supplier<T> dbQuery) {

		CacheManager global = manager(CacheType.GLOBAL);
		CacheWrapper<T> cached = global.get(group, cacheKey);

		// 1. Miss - DB 조회
		if (cached == null) {
			log.debug("캐시 미스 - key: {}", cacheKey);
			recordCacheResult(group, RESULT_MISS);
			return refreshWithLock(group, cacheKey, dbQuery);
		}

		Long remainingTTL = global.getExpire(group, cacheKey);
		if (remainingTTL == null || remainingTTL <= 0) {
			recordCacheResult(group, RESULT_MISS);
			return refreshWithLock(group, cacheKey, dbQuery);
		}

		recordCacheResult(group, RESULT_GLOBAL_HIT);

		// 2. Hit + PER o
		if (isPerTriggered(cached.getTimeToCompute(), remainingTTL)) {
			log.info("PER에 의해 백그라운드에서 캐시를 갱신합니다 - key: {}, remainingTTL: {}", cacheKey, remainingTTL);
			submitRefreshTaskInBackground(cacheKey, () -> refreshGlobalInBackground(group, cacheKey, dbQuery));	// 갱신 위임
		}

		// 3. Hit + PER x
		return cached;
	}

	/**
	 * 락 기반 스템피드 방어 + 락 얻고 나서도 더블 체크
	 */
	private <T> CacheWrapper<T> refreshWithLock(CacheGroup group, String cacheKey, Supplier<T> dbQuery) {

		CacheManager global = manager(CacheType.GLOBAL);
		boolean acquired = false;

		try {
			acquired = redisLockService.tryLock(cacheKey, LOCK_LEASE, LOCK_WAIT);

			// 1. 앞선 리더가 갱신해놓음 - 한번 더 캐시 더블 체크
			CacheWrapper<T> cached = global.get(group, cacheKey);
			if (cached != null) {
				return cached;
			}

			// 2. 락 획득 실패 - 다시 캐시 조회하도록 fallback
			if (!acquired) {
				log.warn("캐시 갱신 락 획득에 실패해 다시 캐시를 조회합니다 - key: {}", cacheKey);
				return getFromGlobal(group, cacheKey, dbQuery);
			}

			// 3. DB 조회
			return refreshGlobalCache(group, cacheKey, dbQuery);
		} finally {
			if (acquired) {
				redisLockService.releaseLock(cacheKey);
			}
		}
	}

	/**
	 * 백그라운드 갱신 Task를 스레드 풀에 제출합니다.
	 * JVM 레벨에서 동일한 Cache Key가 이미 제출되었으면, 풀로 제출하지 않습니다.
	 * 		-> 불필요한 중복 Task 요청으로 인한 포화를 막기 위함
	 *		-> EC2 당 하나의 Task만 제출됨
	 *		-> EC2 여러 대면 글로벌 락으로 하나만 DB 접근하도록 2차적으로 막아야 할듯
	 */
	private void submitRefreshTaskInBackground(String cacheKey, Runnable refreshTask) {

		// putIfAbsent
		if (!inFlightKeys.add(cacheKey)) {
			return;
		}

		try {
			cacheRefreshExecutor.execute(() -> {
				try {
					refreshTask.run();
				} finally {
					inFlightKeys.remove(cacheKey);	// 작업 완료 후 제거
				}
			});
		} catch (RejectedExecutionException e) {
			inFlightKeys.remove(cacheKey);	// 예외 시에도 제거
		}
	}

	/**
	 * 백그라운드에서 DB를 조회하여 글로벌 캐시를 갱신합니다.
	 */
	private <T> void refreshGlobalInBackground(CacheGroup group, String cacheKey, Supplier<T> dbQuery) {

		boolean acquired = false;

		// 여기서 글로벌 락은 왜? -> JVM 레벨에서의 Task 중복 제출은 막음, 그러나 EC2는 여러 대라 Task는 EC2 대수만큼 제출되므르 하나만 DB 접근할 수 있도록 하기 위함
		try {
			acquired = redisLockService.tryLock(cacheKey, LOCK_LEASE);
			if (acquired) {
				refreshGlobalCache(group, cacheKey, dbQuery);
			}
		} catch (Exception e) {
			log.warn("백그라운드 캐시 갱신에 실패했습니다 - key: {}", cacheKey, e);
		} finally {
			if (acquired) {
				redisLockService.releaseLock(cacheKey);
			}
		}
	}

	/**
	 * 백그라운드에서 DB를 조회하여 로컬 캐시를 갱신합니다.
	 * 인스턴스마다 자기 캐시를 따로 채워야 하므로 분산락을 걸지 않습니다.
	 */
	private <T> void refreshLocalInBackground(CacheGroup group, String cacheKey, Supplier<T> dbQuery) {

		try {
			refreshLocalCache(group, cacheKey, dbQuery);
		} catch (Exception e) {
			log.warn("백그라운드 로컬 캐시 갱신에 실패했습니다 - key: {}", cacheKey, e);
		}
	}

	/**
	 * DB에서 값을 조회해 로컬 캐시에 저장합니다.
	 */
	private <T> CacheWrapper<T> refreshLocalCache(CacheGroup group, String cacheKey, Supplier<T> dbQuery) {

		CacheWrapper<T> wrapper = loadFromDb(dbQuery);
		if (wrapper == null) {
			return null;
		}

		manager(CacheType.LOCAL).set(group, cacheKey, wrapper);

		return wrapper;
	}

	/**
	 * DB에서 값을 조회해 글로벌 캐시에 저장합니다.
	 */
	private <T> CacheWrapper<T> refreshGlobalCache(CacheGroup group, String cacheKey, Supplier<T> dbQuery) {

		CacheWrapper<T> wrapper = loadFromDb(dbQuery);
		if (wrapper == null) {
			return null;
		}

		manager(CacheType.GLOBAL).set(group, cacheKey, wrapper);

		// 글로벌 캐시가 새 값으로 바뀌었으므로, 다른 인스턴스가 들고 있는 로컬 캐시는 버리게 한다
		if (group.getCacheType() == CacheType.COMPOSITE) {
			cacheEvictPublisher.publish(group.getCacheName(), cacheKey);
		}

		return wrapper;
	}

	/**
	 * DB 조회에 걸린 시간을 함께 담아 래핑합니다. PER의 timeToCompute로 쓰입니다.
	 */
	private <T> CacheWrapper<T> loadFromDb(Supplier<T> dbQuery) {

		long startMs = System.currentTimeMillis();
		T newValue = dbQuery.get();
		long elapsedMs = System.currentTimeMillis() - startMs;

		if (newValue == null) {
			return null;
		}

		return new CacheWrapper<>(newValue, Math.max(elapsedMs, 1L));
	}

	/**
	 * timeToCompute * beta * -log(rand()) > remainingTTL 이면 미리 갱신합니다.
	 */
	private boolean isPerTriggered(long timeToCompute, long remainingTTL) {

		double randomGap = ThreadLocalRandom.current().nextDouble();
		if (randomGap == 0.0) randomGap = 0.000001;
		double gapScore = timeToCompute * BETA * -Math.log(randomGap);

		return gapScore > remainingTTL;
	}

	private CacheManager manager(CacheType cacheType) {

		CacheManager cacheManager = cacheManagers.get(cacheType);
		if (cacheManager == null) {
			throw new IllegalStateException("등록되지 않은 캐시 계층입니다. cacheType=" + cacheType);
		}

		return cacheManager;
	}

	private <T> T unwrap(CacheWrapper<T> wrapper) {
		return wrapper != null ? wrapper.getCachedData() : null;
	}

	private String getCacheKey(CacheGroup group, String key) {
		return CACHE_PREFIX + group.getCacheName() + ":" + key;
	}

	/**
	 * 캐시 그룹별 조회 결과(local_hit/global_hit/miss)를 집계합니다.
	 */
	private void recordCacheResult(CacheGroup group, String result) {
		meterRegistry.counter(
			CACHE_GETS_METRIC,
			TAG_GROUP, group.getCacheName(),
			TAG_RESULT, result
		).increment();
	}
}
