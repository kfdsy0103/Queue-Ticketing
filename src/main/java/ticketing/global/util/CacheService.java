package ticketing.global.util;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import ticketing.global.enums.CacheGroup;

/**
 * timeToCompute * beta * -log(rand()) > remainingTTL
 * 		1. 캐시 조회
 * 		2. 캐시 생성 시간과 beta(가중치) 부여한 뒤 랜덤한 x값 생성
 * 		3. x 값과 remainingTTL을 비교하여 캐시 갱신 여부 결정
 *
 * 		1. Hit + PER X -> return cache;
 * 		2. Hit + PER O -> 백그라운드 갱신(블로킹 안하기 위해) + return cache;
 *  	3. Miss -> 분산락 + 더블 체크로 스템피드 방어
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

	private final RedisUtil redisUtil;
	private final RedisLockService redisLockService;
	private final Executor cacheRefreshExecutor;

	public CacheService(
		RedisUtil redisUtil,
		RedisLockService redisLockService,
		@Qualifier("cacheRefreshExecutor") Executor cacheRefreshExecutor
	) {
		this.redisUtil = redisUtil;
		this.redisLockService = redisLockService;
		this.cacheRefreshExecutor = cacheRefreshExecutor;
	}

	/**
	 * PER 기반 캐시 조회
	 */
	public <T> T getCacheWithPER(CacheGroup group, String key, Supplier<T> dbQuery) {

		String cacheKey = getCacheKey(group, key);
		CacheWrapper<T> wrappedCache = (CacheWrapper<T>) redisUtil.getObject(cacheKey);

		// 1. Miss - DB 조회
		if (wrappedCache == null) {
			log.debug("캐시 미스 - key: {}", cacheKey);
			return refreshCacheWithLock(group, key, dbQuery);
		}

		Long remainingTTL = redisUtil.getExpire(cacheKey, TimeUnit.MILLISECONDS);
		if (remainingTTL == null || remainingTTL <= 0) {
			return refreshCacheWithLock(group, key, dbQuery);
		}

		// 2. Hit + PER o
		double randomGap = ThreadLocalRandom.current().nextDouble();
		if (randomGap == 0.0) randomGap = 0.000001;
		double gapScore = wrappedCache.getTimeToCompute() * BETA * -Math.log(randomGap);	// timeToCompute * beta * -log(rand()) > remainingTTL

		if (gapScore > remainingTTL) {
			log.info("PER에 의해 백그라운드에서 캐시를 갱신합니다 - key: {}, remainingTTL: {}", cacheKey, remainingTTL);
			cacheRefreshExecutor.execute(() -> refreshInBackground(group, cacheKey, dbQuery));	// 갱신 위임
			return wrappedCache.getCachedData();
		}

		// 3. Hit + PER x
		return wrappedCache.getCachedData();
	}

	/**
	 * 락 기반 스템피드 방어 + 락 얻고 나서도 더블 체크
	 */
	private <T> T refreshCacheWithLock(CacheGroup group, String key, Supplier<T> dbQuery) {

		String cacheKey = getCacheKey(group, key);
		boolean acquired = false;

		try {
			acquired = redisLockService.tryLock(cacheKey, LOCK_LEASE, LOCK_WAIT);

			// 1. 앞선 리더가 갱신해놓음 - 한번 더 캐시 더블 체크
			CacheWrapper<T> cached = (CacheWrapper<T>) redisUtil.getObject(cacheKey);
			if (cached != null) {
				return cached.getCachedData();
			}

			// 2. 락 획득 실패 - 다시 캐시 조회하도록 fallback
			if (!acquired) {
				log.warn("캐시 갱신 락 획득에 실패해 다시 캐시를 조회합니다 - key: {}", cacheKey);
				return getCacheWithPER(group, key, dbQuery);
			}

			// 3. DB 조회
			return refreshCache(group, cacheKey, dbQuery);
		} finally {
			if (acquired) {
				redisLockService.releaseLock(cacheKey);
			}
		}
	}

	/**
	 * 백그라운드에서 DB를 조회하여 캐시를 갱신합니다.
	 */
	private <T> void refreshInBackground(CacheGroup group, String cacheKey, Supplier<T> dbQuery) {

		boolean acquired = false;

		try {
			acquired = redisLockService.tryLock(cacheKey, LOCK_LEASE);
			if (acquired) {
				refreshCache(group, cacheKey, dbQuery);
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
	 * DB에서 값을 조회해 캐시에 저장합니다.
	 */
	private <T> T refreshCache(CacheGroup group, String cacheKey, Supplier<T> dbQuery) {

		long startMs = System.currentTimeMillis();
		T newValue = dbQuery.get();
		long elapsedMs = System.currentTimeMillis();
		long timeToCompute = (elapsedMs - startMs) > 0 ? (elapsedMs - startMs) : 1;

		if (newValue != null) {
			Duration ttl = JitterUtil.applyJitter(group.getExpiredAfterWrite());
			CacheWrapper<T> cacheData = new CacheWrapper<>(newValue, timeToCompute);
			redisUtil.set(cacheKey, cacheData, ttl);
		}

		return newValue;
	}

	private String getCacheKey(CacheGroup group, String key) {
		return CACHE_PREFIX + group.getKeyPrefix() + ":" + key;
	}
}
