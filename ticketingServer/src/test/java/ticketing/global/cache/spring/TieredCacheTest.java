package ticketing.global.cache.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import io.micrometer.core.instrument.MeterRegistry;
import ticketing.config.RedisTestContainersConfig;
import ticketing.global.cache.constants.CacheName;
import ticketing.global.cache.enums.CacheGroup;
import ticketing.global.cache.eviction.CacheEvictPublisher;
import ticketing.global.cache.manager.CaffeineCacheStore;
import ticketing.global.cache.manager.RedisCacheStore;
import ticketing.global.cache.service.SingleFlightExecutor;
import ticketing.global.util.RedisLockService;

/**
 * L1(Caffeine) + L2(Redis) 2레벨 캐시 동작 검증 (TieredCache 기준, CacheServiceTest에서 이관)
 * 		DB 조회는 Callable로 주입되므로, 실제 DB 대신 호출 횟수를 세는 가짜 조회를 넘긴다.
 */
@SpringBootTest
@ActiveProfiles("test")
class TieredCacheTest extends RedisTestContainersConfig {

	@Autowired
	private TieredCacheManager tieredCacheManager;

	@Autowired
	private MeterRegistry meterRegistry;
	@Autowired
	private RedisLockService redisLockService;
	@Autowired
	private CaffeineCacheStore localCacheStore;
	@Autowired
	private RedisTemplate<String, Object> redisTemplate;
	@Autowired
	@Qualifier("cacheRefreshExecutor")
	private Executor cacheRefreshExecutor;

	@MockitoSpyBean	 // when 사용 시 doReturn -> when 유의
	private RedisCacheStore globalCacheStore;	// get() count 측정용

	private final CacheGroup CACHE_GROUP = CacheGroup.CONCERT_DETAIL;							// COMPOSITE (L1 + L2)
	private final String CACHE_KEY_ID = "1";													// concertId
	private final String CACHE_KEY = "cache:" + CacheName.CONCERT_DETAIL + ":" + CACHE_KEY_ID;	// 캐시 Key
	private final String DB_VALUE = "value";													// 원본 Value

	private TieredCache tieredCache;	// 스프링 컨텍스트가 관리하는 TieredCacheManager가 만든 인스턴스

	private final AtomicInteger dbCallCount = new AtomicInteger();		// DB access 카운트 용
	private final Supplier<String> dbQuery = () -> {
		dbCallCount.incrementAndGet();
		try {
			Thread.sleep(50);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return DB_VALUE;
	};

	@BeforeEach
	void init() {
		tieredCache = (TieredCache) tieredCacheManager.getCache(CacheName.CONCERT_DETAIL);
		localCacheStore.clear(CACHE_GROUP);
		redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
		dbCallCount.set(0);
		reset(globalCacheStore);
	}

	@Test
	@DisplayName("웜업 후 요청이 들어오면 L1(로컬)에서 처리된다")
	void 로컬_방어() {
		// given
		tieredCache.get(CACHE_KEY_ID, dbQuery::get);	// L1 + L2 웜업, DB count++
		reset(globalCacheStore);	// global count 0으로

		// when
		String result = tieredCache.get(CACHE_KEY_ID, dbQuery::get);	// global count X, DB count X

		// then - L1 히트면 그 자리에서 반환하므로 L2는 건드리지 않는다
		assertThat(result).isEqualTo(DB_VALUE);
		assertThat(dbCallCount.get()).isEqualTo(1);
		verify(globalCacheStore, never()).get(any(), anyString());
	}

	@Test
	@DisplayName("L1(로컬)에서 미스가 나면 L2(Redis)에서 처리된다")
	void 로컬_미스_글로벌_방어() {
		// given
		tieredCache.get(CACHE_KEY_ID, dbQuery::get);	// L1 + L2 웜업, DB count++
		localCacheStore.clear(CACHE_GROUP);	// L1 miss
		reset(globalCacheStore);	// global count 0으로

		// when
		String result = tieredCache.get(CACHE_KEY_ID, dbQuery::get);	// global count++, DB count X

		// then
		assertThat(result).isEqualTo(DB_VALUE);
		assertThat(dbCallCount.get()).isEqualTo(1);
		verify(globalCacheStore, times(1)).get(any(), anyString());
	}

	@Test
	@DisplayName("L1(로컬)에서 미스가 나고, 1000명이 몰리는 상황에서 SingleFlight로 한 스레드만 L2에 접근한다")
	void 로컬_미스_SingleFlight_방어() throws InterruptedException {
		// given
		tieredCache.get(CACHE_KEY_ID, dbQuery::get);	// L1 + L2 웜업, DB count++
		localCacheStore.clear(CACHE_GROUP);	// L1 miss
		reset(globalCacheStore);	// global count 0으로

		// when
		concurrentRequests(List.of(tieredCache));	// global count +1, DB count X

		// then
		assertThat(dbCallCount.get()).isEqualTo(1);
		verify(globalCacheStore, times(1)).get(any(), anyString());
	}

	@Test
	@DisplayName("인스턴스 1대 L1, L2가 모두 Miss 상태, 1000명이 몰려도 DB에는 한 번만 접근한다")
	void 단일노드_로컬_미스_글로벌_미스_스탬피드_방어() throws InterruptedException {
		// no warm-up
		// when
		concurrentRequests(List.of(tieredCache));	// DB count +1

		// then
		assertThat(dbCallCount.get()).isEqualTo(1);
	}

	@Test
	@DisplayName("인스턴스 3대 L1, L2가 모두 Miss 상태, 1000명이 몰려도 DB에는 한 번만 접근한다")
	void 멀티노드_로컬_미스_글로벌_미스_스탬피드_방어() throws InterruptedException {
		// no warm-up
		// when, DB count +1
		concurrentRequests(
			List.of(
				createFakeEC2Instance(),
				createFakeEC2Instance(),
				createFakeEC2Instance()
			)
		);

		// then
		assertThat(dbCallCount.get()).isEqualTo(1);
	}

	@Test
	@DisplayName("다른 인스턴스가 글로벌 캐시를 갱신하면 pub/sub에 의해 내 로컬이 무효화된다")
	void 글로벌_갱신_로컬_무효화_pubsub() {
		// given
		tieredCache.get(CACHE_KEY_ID, dbQuery::get);	// L1 + L2 웜업, DB count++
		globalCacheStore.evict(CACHE_GROUP, CACHE_KEY);		// L2 캐시 제거

		// when
		TieredCache otherInstance = createFakeEC2Instance();			// L1 없는 다른 인스턴스
		otherInstance.get(CACHE_KEY_ID, dbQuery::get);	// L1 Miss -> L2 Miss -> DB 조회하여 Global 갱신할 것

		// then
		await().atMost(Duration.ofSeconds(3))
			.pollInterval(Duration.ofMillis(100))
			.untilAsserted(() -> assertThat(localCacheStore.get(CACHE_GROUP, CACHE_KEY)).isNull());	// pub/sub에 의해 로컬이 무효화 됨
	}

	/**
	 * 인스턴스 여러 대에 나누어 TieredCache.get()
	 */
	private void concurrentRequests(List<TieredCache> instances) throws InterruptedException {

		final int numThreads = 1000;	// 1000명의 동시 요청

		ExecutorService executorService = Executors.newFixedThreadPool(numThreads);

		CountDownLatch readyLatch = new CountDownLatch(numThreads);
		CountDownLatch startTrigger = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(numThreads);

		for (int i = 0; i < numThreads; i++) {
			int index = i;
			executorService.submit(() -> {
				readyLatch.countDown();
				try {
					startTrigger.await();
					TieredCache cache = instances.get(index % instances.size());
					cache.get(CACHE_KEY_ID, dbQuery::get);
				} catch (Exception e) {
					System.out.println("Thread error occured.");
					Thread.currentThread().interrupt();
				} finally {
					doneLatch.countDown();
				}
			});
		}

		readyLatch.await();			// for all-threads ready
		startTrigger.countDown();	// 동시에
		doneLatch.await();			// 대기

		executorService.shutdown();
	}

	/**
	 * EC2 여러 대를 흉내내기 위해, 로컬 캐시 및 SingleFlight 클래스를 개별로 갖는 TieredCache 생성
	 */
	private TieredCache createFakeEC2Instance() {
		return new TieredCache(
			CACHE_GROUP,
			new CaffeineCacheStore(),					// 개별 로컬 캐시
			globalCacheStore,							// 공유 글로벌(스파이)
			redisLockService,
			new SingleFlightExecutor(),				// 개별 ConcurrentHashMap
			new CacheEvictPublisher(redisTemplate),	// 개별 instanceId 부여
			cacheRefreshExecutor,
			meterRegistry
		);
	}
}
