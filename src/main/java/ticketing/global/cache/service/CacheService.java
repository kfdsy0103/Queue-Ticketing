package ticketing.global.cache.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.cache.constants.CacheMetric;
import ticketing.global.cache.exception.CacheErrorCode;
import ticketing.global.cache.enums.CacheGroup;
import ticketing.global.cache.enums.CacheType;
import ticketing.global.cache.eviction.CacheEvictPublisher;
import ticketing.global.cache.manager.CacheManager;
import ticketing.global.cache.dto.CacheResult;
import ticketing.global.cache.dto.CacheEntry;
import ticketing.global.util.RedisLockService;

/**
 * 캐시 저장/획득 관련 오케스트레이션 역할, 기본적으로 PER로 동작하도록 함
 * 구체적인 접근은 CacheManager 구현체에 위임
 *
 * PER = timeToCompute * beta * -log(rand()) > remainingTTL
 * 		1. Hit + PER X -> return cache;
 * 		2. Hit + PER O -> 블로킹을 없애기 위해 백그라운드에 갱신 위임 + return cache;
 * 		3. Miss -> 로컬이면 SingleFlight, 글로벌이면 분산락으로 스템피드 방어하면서 조회
 *
 * 캐시 유형에 따른 스템피드 방어 전략
 * 		1. LOCAL: 단일 인스턴스를 의미하니까 ConcurrentHashMap 기반 SingleFlight 패턴으로 방어
 * 		2. GLOBAL: 글로벌이니 분산락으로 스템피드 방어
 * 		3. COMPOSITE: 로컬캐시(L1) -> 글로벌캐시(L2) -> DB이니까, L1-L2는 SingleFlight, L2-DB는 분산락으로 스템피드 방어
 *
 * 스템피드 대응 시 주의사항
 * 		- 락 얻고 나서 캐시 한번 더 더블 체크 해줘야 한다.
 * 		- 더블체크 안하면, 대기하던 모든 인원이 락 얻고 진입해서 DB로 쏠리기에 스템피드 재발
 *
 * PER + 백그라운드 갱신시 고려한 점
 * 		최대한 락으로 인한 블로킹 없이 빠르게 반환하자는게 목적이니까
 * 		1. JVM 레벨에서 ConcurrentHashMap을 두어서 putIfAbsent로 백그라운드로 Task가 한번만 제출되도록 함
 * 		2. 1번에 의하여 EC2당 하나의 Task를 갖게 될 것이고, EC2 대수만큼 DB에 접근할 것
 * 			-> 또 다른 스템피드? 이거는 분산락으로 막기
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

	private final MeterRegistry meterRegistry;								// Cache 관련 메트릭 수집용
	private final RedisLockService redisLockService;						// RedisLockService
	private final Map<CacheType, CacheManager> cacheManagers;				// 캐시 접근 구현체
	private final CacheEvictPublisher cacheEvictPublisher;					// L1+L2 구조에서 로컬 캐시 간 일관성을 맞추려면 pub/sub으로 무효화
	private final Executor cacheRefreshExecutor;							// 캐시를 갱신하는 백그라운드 스레드
	private final SingleFlightExecutor singleFlightExecutor;				// JVM 레벨에서 스템피드 방어 목적 (ConcurrentHashMap)
	private final Set<String> inFlightKeys = ConcurrentHashMap.newKeySet();	// 백그라운드로 Task 제출 시 한번만 제출하기 위함

	public CacheService(
		MeterRegistry meterRegistry,
		RedisLockService redisLockService,
		List<CacheManager> cacheManagers,
		CacheEvictPublisher cacheEvictPublisher,
		@Qualifier("cacheRefreshExecutor") Executor cacheRefreshExecutor,
		SingleFlightExecutor singleFlightExecutor
	) {
		this.meterRegistry = meterRegistry;
		this.redisLockService = redisLockService;
		this.cacheManagers = cacheManagers.stream()
			.collect(Collectors.toUnmodifiableMap(CacheManager::getSupportedType, Function.identity()));
		this.cacheEvictPublisher = cacheEvictPublisher;
		this.cacheRefreshExecutor = cacheRefreshExecutor;
		this.singleFlightExecutor = singleFlightExecutor;
	}

	/**
	 * 캐시 유형 별 조회 담당
	 */
	public <T> T getCacheWithPER(CacheGroup group, String key, Supplier<T> dbQuery) {

		String cacheKey = getCacheKey(group, key);	// 캐시 Key
		CacheType cacheType = group.getCacheType();	// 캐시 유형 (Local or Global or Composite)

		CacheResult<T> result;
		if (cacheType == CacheType.LOCAL) {
			result = getCacheFromLocal(group, cacheKey, dbQuery);	// 로컬 캐시인 경우
		} else if (cacheType == CacheType.GLOBAL) {
			result = getCacheFromGlobal(group, cacheKey, dbQuery);	// 글로벌 캐시인 경우
		} else {
			result = getCacheFromComposite(group, cacheKey, dbQuery);	// L1 + L2 캐시인 경우
		}

		recordCacheMetric(group, result.getMetricResult());	// hit, miss 메트릭 기록

		CacheEntry<T> cacheEntry = result.getEntry();

		return (cacheEntry != null) ? cacheEntry.getCachedData() : null;
	}

	/**
	 * 로컬 <-> DB, PER + SingleFlight
	 */
	private <T> CacheResult<T> getCacheFromLocal(CacheGroup group, String cacheKey, Supplier<T> dbQuery) {

		// 로컬 캐시 매니저 획득
		CacheManager cacheManager = getCacheManager(CacheType.LOCAL);

		// 캐시 획득 및 PER 계산을 위해 remainingTTL 획득
		CacheEntry<T> cached = cacheManager.get(group, cacheKey);
		Long remainingTTL = (cached != null) ? cacheManager.getExpire(group, cacheKey) : null;

		// 1. Miss - singleFlight 활용하여 하나의 스레드만 DB 접근하도록
		if (cached == null || remainingTTL == null || remainingTTL <= 0) {
			CacheEntry<T> loaded = singleFlightExecutor.execute(
				cacheKey,
				() -> {
					CacheEntry<T> refreshed = cacheManager.get(group, cacheKey);	// 캐시 더블 체크
					if (refreshed != null) {
						return refreshed;
					}

					CacheEntry<T> cacheEntry = loadFromDB(dbQuery);	// 직접 조회
					saveIntoLocal(group, cacheKey, cacheEntry);

					return cacheEntry;
				}
			);

			return new CacheResult<>(loaded, CacheMetric.RESULT_MISS);
		}

		// 2. Hit + PER o - 갱신은 백그라운드로 위임 후, 아래 return cache;
		if (shouldRecompute(cached.getTimeToCompute(), remainingTTL)) {
			log.info("PER에 의해 백그라운드에서 로컬 캐시를 갱신합니다 - key: {}, remainingTTL: {}", cacheKey, remainingTTL);
			submitRefreshTaskInBackground(
				cacheKey,
				() -> {
					CacheEntry<T> cacheEntry = loadFromDB(dbQuery);
					saveIntoLocal(group, cacheKey, cacheEntry);
				}
			);
		}

		// 3. Hit + PER x - return cache;
		return new CacheResult<>(cached, CacheMetric.RESULT_LOCAL_HIT);
	}

	/**
	 * 글로벌 <-> DB, PER + 분산락으로 스템피드 방어
	 */
	private <T> CacheResult<T> getCacheFromGlobal(CacheGroup group, String cacheKey, Supplier<T> dbQuery) {

		// 글로벌 캐시 매니저 획득
		CacheManager cacheManager = getCacheManager(CacheType.GLOBAL);

		// 캐시 획득 및 PER 계산을 위해 remainingTTL 획득
		CacheEntry<T> cached = cacheManager.get(group, cacheKey);
		Long remainingTTL = (cached != null) ? cacheManager.getExpire(group, cacheKey) : null;

		// 1. Miss - 분산락으로 하나의 스레드만 DB 접근하도록 (더블 체크 안하면 다시 스템피드)
		if (cached == null || remainingTTL == null || remainingTTL <= 0) {

			boolean acquired = redisLockService.tryLock(cacheKey, LOCK_LEASE, LOCK_WAIT);

			try {
				// 락 상관 없이 캐시 한번 더 더블 체크
				CacheEntry<T> refreshed = cacheManager.get(group, cacheKey);
				if (refreshed != null) {
					return new CacheResult<>(refreshed, CacheMetric.RESULT_MISS);
				}

				if (!acquired) {
					log.warn("캐시 갱신 락 획득에 실패했지만 캐시가 비어 있어 DB로 조회합니다 - key: {}", cacheKey);
				}

				CacheEntry<T> cacheEntry = loadFromDB(dbQuery);
				saveIntoGlobal(group, cacheKey, cacheEntry);

				return new CacheResult<>(cacheEntry, CacheMetric.RESULT_MISS);
			} finally {
				if (acquired) {
					redisLockService.releaseLock(cacheKey);
				}
			}
		}

		// 2. Hit + PER o - Task는 인스턴스마다 제출되므로 분산락으로 하나만 DB에 보낸다
		if (shouldRecompute(cached.getTimeToCompute(), remainingTTL)) {
			log.info("PER에 의해 백그라운드에서 글로벌 캐시를 갱신합니다 - key: {}, remainingTTL: {}", cacheKey, remainingTTL);

			submitRefreshTaskInBackground(cacheKey, () -> {
				// 여러 EC2 간 Task 사이에서 하나만 DB 접근하도록 스템피드 방어
				if (!redisLockService.tryLock(cacheKey, LOCK_LEASE)) {
					return;
				}
				
				try {
					CacheEntry<T> cacheEntry = loadFromDB(dbQuery);	// DB 조회
					saveIntoGlobal(group, cacheKey, cacheEntry);	// 글로벌 캐시 갱신
				} finally {
					redisLockService.releaseLock(cacheKey);
				}
			});
		}

		// 3. Hit + PER x - return cache;
		return new CacheResult<>(cached, CacheMetric.RESULT_GLOBAL_HIT);
	}

	/**
	 * 로컬 캐시 <-> 글로벌 캐시 <-> DB,
	 * 		로컬 -> 글로벌은 SingleFlight로 하나의 스레드만 레디스 조회하도록 방어
	 * 		글로벌 -> DB는 분산락으로 방어
	 */
	private <T> CacheResult<T> getCacheFromComposite(CacheGroup group, String cacheKey, Supplier<T> dbQuery) {

		// 로컬 캐시 매니저 획득
		CacheManager localCacheManager = getCacheManager(CacheType.LOCAL);

		// 1. 로컬 Hit
		CacheEntry<T> localCacheEntry = localCacheManager.get(group, cacheKey);
		if (localCacheEntry != null) {
			return new CacheResult<>(localCacheEntry, CacheMetric.RESULT_LOCAL_HIT);
		}

		// 2. 로컬 Miss - 글로벌 캐시에서 가져온다
		// 		인스턴스 레벨에서는 singleFlight로 글로벌 캐시에 대한 스템피드 방어
		// 		글로벌 -> DB는 분산락으로 스템피드 방어
		return singleFlightExecutor.execute(cacheKey, () -> {

			// 더블 체크
			CacheEntry<T> refreshCacheEntry = localCacheManager.get(group, cacheKey);
			if (refreshCacheEntry != null) {
				return new CacheResult<>(refreshCacheEntry, CacheMetric.RESULT_MISS);
			}

			// singleFlight로 하나만 글로벌 캐시에 접근하여 로컬에 동기화
			CacheResult<T> result = getCacheFromGlobal(group, cacheKey, dbQuery);
			saveIntoLocal(group, cacheKey, result.getEntry());

			return result;
		});
	}

	/**
	 * 캐시 refresh Task를 비동기적으로 백그라운드에서 처리합니다.
	 * putIfAbsent로 같은 키가 이미 제출되어 있으면 제출하지 않아 불필요한 제출을 막습니다.
	 */
	private void submitRefreshTaskInBackground(String cacheKey, Runnable refreshTask) {

		// putIfAbsent (JVM 레벨에서 백그라운드 갱신 Task는 Key당 하나만 제출되도록, 락 블로깅 없이 처리)
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
	 * DB에서 조회한 엔트리를 넘겨받아 로컬 캐시에 저장합니다.
	 */
	private <T> void saveIntoLocal(CacheGroup group, String cacheKey, CacheEntry<T> cacheEntry) {
		if (cacheEntry == null) {
			return;
		}

		// 로컬 개시 갱신
		CacheManager cacheManager = getCacheManager(CacheType.LOCAL);
		cacheManager.set(group, cacheKey, cacheEntry);
	}

	/**
	 * DB에서 조회한 엔트리를 넘겨받아 글로벌 캐시에 저장합니다.
	 * COMPOSITE 캐시인 경우 pub/sub으로 로컬 캐시 무효화를 발행합니다.
	 */
	private <T> void saveIntoGlobal(CacheGroup group, String cacheKey, CacheEntry<T> cacheEntry) {
		if (cacheEntry == null) {
			return;
		}

		// 글로벌 캐시 갱신
		CacheManager cacheManager = getCacheManager(CacheType.GLOBAL);
		cacheManager.set(group, cacheKey, cacheEntry);

		// L1 + L2 캐시이면, 로컬 캐시에 무효화 pub/sub
		if (group.getCacheType() == CacheType.COMPOSITE) {
			CacheManager localCacheManager = getCacheManager(CacheType.LOCAL);
			localCacheManager.set(group, cacheKey, cacheEntry);
			cacheEvictPublisher.publish(group.getCacheName(), cacheKey);
		}
	}

	/**
	 * DB에서 조회 및 PER에 필요한 timeToCompute도 계산하여 CacheEntry 반환
	 */
	private <T> CacheEntry<T> loadFromDB(Supplier<T> dbQuery) {
		long startMs = System.currentTimeMillis();
		T newValue = dbQuery.get();
		long endMs = System.currentTimeMillis();
		long elapsedMs = endMs - startMs;

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
	 * 캐시 유형에 맞는 캐시 매니저 획득
	 */
	private CacheManager getCacheManager(CacheType cacheType) {
		CacheManager cacheManager = cacheManagers.get(cacheType);
		if (cacheManager == null) {
			log.error("등록되지 않은 캐시 계층입니다 - cacheType: {}", cacheType);
			throw new GeneralException(CacheErrorCode.CACHE_MANAGER_NOT_FOUND);
		}
		return cacheManager;
	}

	private String getCacheKey(CacheGroup group, String key) {
		return CACHE_PREFIX + group.getCacheName() + ":" + key;
	}

	private void recordCacheMetric(CacheGroup group, String result) {
		meterRegistry.counter(
			CacheMetric.CACHE_GETS_METRIC,
			CacheMetric.TAG_GROUP, group.getCacheName(),
			CacheMetric.TAG_RESULT, result
		).increment();
	}
}
