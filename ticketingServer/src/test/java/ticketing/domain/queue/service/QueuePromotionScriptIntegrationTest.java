package ticketing.domain.queue.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.context.ActiveProfiles;

import ticketing.config.RedisTestContainersConfig;
import ticketing.domain.queue.constants.QueueRedisKeys;
import ticketing.global.util.RedisUtil;

/**
 * promote-queue.lua 가 대기열에서 순번대로 꺼내 Active 로 옮기는 과정이 원자적인지 실제 Redis 에서 검증한다.
 * DB 는 비어 있어 QueuePromotionScheduler 가 돌아도 조회할 회차가 없다.
 */
@SpringBootTest
@ActiveProfiles("test")
class QueuePromotionScriptIntegrationTest extends RedisTestContainersConfig {

	private static final RedisScript<Long> PROMOTE_SCRIPT =
		RedisScript.of(new ClassPathResource("luaScripts/promote-queue.lua"), Long.class);

	private static final Long SCHEDULE_ID = 1L;
	private static final long TTL_SECONDS = 420L;

	@Autowired
	private RedisUtil redisUtil;

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	@BeforeEach
	void flushRedis() {
		redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
	}

	private void enqueue(Long userId, double order, String sessionId) {
		redisUtil.zAdd(QueueRedisKeys.waitingKey(SCHEDULE_ID), userId, order);
		if (sessionId != null) {
			redisUtil.set(QueueRedisKeys.userInfoKeyPrefix(SCHEDULE_ID) + userId, sessionId, Duration.ofMinutes(30));
		}
	}

	private Long promote(long batchSize) {
		return redisUtil.execute(
			PROMOTE_SCRIPT,
			List.of(
				QueueRedisKeys.waitingKey(SCHEDULE_ID),
				QueueRedisKeys.activeKeyPrefix(SCHEDULE_ID),
				QueueRedisKeys.userInfoKeyPrefix(SCHEDULE_ID)
			),
			batchSize,
			TTL_SECONDS
		);
	}

	private String activeSession(Long userId) {
		return redisUtil.get(QueueRedisKeys.activeKey(SCHEDULE_ID, userId));
	}

	@Test
	void 대기열_순번이_빠른_사용자부터_배치_크기만큼_승격된다() {
		// given
		enqueue(1L, 1, "session-1");
		enqueue(2L, 2, "session-2");
		enqueue(3L, 3, "session-3");

		// when
		Long promoted = promote(2);

		// then
		assertThat(promoted).isEqualTo(2L);
		assertThat(activeSession(1L)).isEqualTo("session-1");
		assertThat(activeSession(2L)).isEqualTo("session-2");
		assertThat(activeSession(3L)).isNull();
		assertThat(redisUtil.zRank(QueueRedisKeys.waitingKey(SCHEDULE_ID), 3L)).isZero();
	}

	@Test
	void 승격된_사용자의_세션ID가_작업열_키에_복사되고_TTL이_걸린다() {
		// given
		enqueue(1L, 1, "session-1");

		// when
		promote(15);

		// then
		assertThat(activeSession(1L)).isEqualTo("session-1");
		assertThat(redisUtil.getExpire(QueueRedisKeys.activeKey(SCHEDULE_ID, 1L))).isBetween(1L, TTL_SECONDS);
	}

	@Test
	void 대기열이_비어있으면_0을_반환한다() {
		// when
		Long promoted = promote(15);

		// then
		assertThat(promoted).isZero();
	}

	@Test
	void 대기_인원이_배치_크기보다_적으면_있는_만큼만_승격된다() {
		// given
		enqueue(1L, 1, "session-1");
		enqueue(2L, 2, "session-2");

		// when
		Long promoted = promote(15);

		// then
		assertThat(promoted).isEqualTo(2L);
		assertThat(redisUtil.hasKey(QueueRedisKeys.waitingKey(SCHEDULE_ID))).isFalse();
	}

	@Test
	void 세션정보가_없는_사용자는_승격_수에서_빠지고_대기열에서도_제거된다() {
		// given
		enqueue(1L, 1, null);
		enqueue(2L, 2, "session-2");

		// when
		Long promoted = promote(15);

		// then
		assertThat(promoted).isEqualTo(1L);
		assertThat(activeSession(1L)).isNull();
		assertThat(activeSession(2L)).isEqualTo("session-2");
		assertThat(redisUtil.zRank(QueueRedisKeys.waitingKey(SCHEDULE_ID), 1L)).isNull();
	}

	@Test
	void 동시에_여러번_승격해도_대기_인원보다_많이_승격되지_않는다() throws InterruptedException {
		// given
		int waitingCount = 30;
		for (int i = 1; i <= waitingCount; i++) {
			enqueue((long)i, i, "session-" + i);
		}

		int threadCount = 10;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threadCount);
		AtomicLong totalPromoted = new AtomicLong();

		// when
		for (int i = 0; i < threadCount; i++) {
			executor.execute(() -> {
				try {
					start.await();
					Long promoted = promote(15);
					if (promoted != null) {
						totalPromoted.addAndGet(promoted);
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} finally {
					done.countDown();
				}
			});
		}
		start.countDown();
		done.await(30, TimeUnit.SECONDS);
		executor.shutdown();

		// then
		assertThat(totalPromoted.get()).isEqualTo(waitingCount);
		assertThat(redisUtil.hasKey(QueueRedisKeys.waitingKey(SCHEDULE_ID))).isFalse();
	}
}
