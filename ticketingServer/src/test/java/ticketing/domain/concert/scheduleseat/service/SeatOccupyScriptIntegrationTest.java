package ticketing.domain.concert.scheduleseat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.context.ActiveProfiles;

import ticketing.config.RedisTestContainersConfig;
import ticketing.domain.concert.scheduleseat.constants.ScheduleSeatRedisKeys;
import ticketing.global.util.RedisUtil;

/**
 * occupy-seats.lua 의 all-or-nothing 점유와 verify-occupy.lua 의 소유자 검증을 실제 Redis 에서 검증한다.
 * DB 는 비어 있어 스케쥴러가 돌아도 이 테스트의 Redis 키를 건드리지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SeatOccupyScriptIntegrationTest extends RedisTestContainersConfig {

	private static final RedisScript<Long> OCCUPY_SCRIPT =
		RedisScript.of(new ClassPathResource("luaScripts/occupy-seats.lua"), Long.class);
	private static final RedisScript<Long> VERIFY_SCRIPT =
		RedisScript.of(new ClassPathResource("luaScripts/verify-occupy.lua"), Long.class);

	private static final Long SCHEDULE_ID = 1L;
	private static final long TTL_SECONDS = 60L;

	@Autowired
	private RedisUtil redisUtil;

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	@BeforeEach
	void flushRedis() {
		redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
	}

	private Long occupy(Long userId, List<Long> seatIds) {
		List<String> keys = new ArrayList<>(seatIds.stream().map(ScheduleSeatRedisKeys::occupyKey).toList());
		keys.add(ScheduleSeatRedisKeys.userOccupyKey(userId));
		keys.add(ScheduleSeatRedisKeys.scheduleOccupyKey(SCHEDULE_ID));

		List<Object> args = new ArrayList<>();
		args.add(userId);
		args.add(TTL_SECONDS);
		args.add(System.currentTimeMillis() + TTL_SECONDS * 1000);
		args.addAll(seatIds);

		return redisUtil.execute(OCCUPY_SCRIPT, keys, args.toArray());
	}

	private Long verify(Long userId, List<Long> seatIds) {
		return redisUtil.execute(
			VERIFY_SCRIPT,
			seatIds.stream().map(ScheduleSeatRedisKeys::occupyKey).toList(),
			userId
		);
	}

	private String owner(Long seatId) {
		return redisUtil.get(ScheduleSeatRedisKeys.occupyKey(seatId));
	}

	@Nested
	@DisplayName("occupy-seats.lua")
	class OccupySeats {

		@Test
		void 비어있는_좌석을_점유하면_좌석키와_사용자_회차_인덱스가_모두_채워진다() {
			// when
			Long result = occupy(1L, List.of(10L, 11L));

			// then
			assertThat(result).isEqualTo(1L);
			assertThat(owner(10L)).isEqualTo("1");
			assertThat(owner(11L)).isEqualTo("1");
			assertThat(redisUtil.zRangeByScoreFrom(
				ScheduleSeatRedisKeys.userOccupyKey(1L), System.currentTimeMillis())).hasSize(2);
			assertThat(redisUtil.zRangeByScoreFrom(
				ScheduleSeatRedisKeys.scheduleOccupyKey(SCHEDULE_ID), System.currentTimeMillis())).hasSize(2);
		}

		@Test
		void 하나라도_점유되어_있으면_전체가_실패하고_나머지_좌석은_그대로_비어있다() {
			// given
			occupy(1L, List.of(11L));

			// when
			Long result = occupy(2L, List.of(10L, 11L, 12L));

			// then
			assertThat(result).isZero();
			assertThat(redisUtil.hasKey(ScheduleSeatRedisKeys.occupyKey(10L))).isFalse();
			assertThat(redisUtil.hasKey(ScheduleSeatRedisKeys.occupyKey(12L))).isFalse();
			assertThat(owner(11L)).isEqualTo("1");
			assertThat(redisUtil.zRangeByScoreFrom(
				ScheduleSeatRedisKeys.userOccupyKey(2L), System.currentTimeMillis())).isEmpty();
		}

		@Test
		void 겹치는_좌석이_하나뿐이어도_전체가_실패한다() {
			// given
			occupy(1L, List.of(15L));

			// when
			Long result = occupy(2L, List.of(15L, 16L, 17L, 18L));

			// then
			assertThat(result).isZero();
			assertThat(redisUtil.hasKey(ScheduleSeatRedisKeys.occupyKey(16L))).isFalse();
			assertThat(redisUtil.hasKey(ScheduleSeatRedisKeys.occupyKey(17L))).isFalse();
			assertThat(redisUtil.hasKey(ScheduleSeatRedisKeys.occupyKey(18L))).isFalse();
		}

		@Test
		void 본인이_이미_점유한_좌석도_다시_점유할_수_없다() {
			// given
			occupy(1L, List.of(10L));

			// when
			Long result = occupy(1L, List.of(10L, 11L));

			// then
			assertThat(result).isZero();
			assertThat(redisUtil.hasKey(ScheduleSeatRedisKeys.occupyKey(11L))).isFalse();
		}

		@Test
		void 점유에_성공하면_좌석키와_두_인덱스에_모두_TTL이_걸린다() {
			// when
			occupy(1L, List.of(10L));

			// then
			assertThat(redisUtil.getExpire(ScheduleSeatRedisKeys.occupyKey(10L))).isBetween(1L, TTL_SECONDS);
			assertThat(redisUtil.getExpire(ScheduleSeatRedisKeys.userOccupyKey(1L))).isBetween(1L, TTL_SECONDS);
			assertThat(redisUtil.getExpire(ScheduleSeatRedisKeys.scheduleOccupyKey(SCHEDULE_ID)))
				.isBetween(1L, TTL_SECONDS);
		}

		@Test
		void 여러_사용자가_동시에_같은_좌석을_점유하면_정확히_한_명만_성공한다() throws InterruptedException {
			// given
			int threadCount = 20;
			ExecutorService executor = Executors.newFixedThreadPool(threadCount);
			CountDownLatch start = new CountDownLatch(1);
			CountDownLatch done = new CountDownLatch(threadCount);
			AtomicInteger successCount = new AtomicInteger();

			// when
			for (int i = 0; i < threadCount; i++) {
				long userId = i + 1L;
				executor.execute(() -> {
					try {
						start.await();
						Long result = occupy(userId, List.of(10L, 11L));
						if (result != null && result == 1L) {
							successCount.incrementAndGet();
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
			assertThat(successCount.get()).isEqualTo(1);
			assertThat(owner(10L)).isNotNull();
			assertThat(owner(10L)).isEqualTo(owner(11L));
		}
	}

	@Nested
	@DisplayName("verify-occupy.lua")
	class VerifyOccupy {

		@Test
		void 본인이_점유한_좌석들만_넘기면_1을_반환한다() {
			// given
			occupy(1L, List.of(10L, 11L));

			// when
			Long result = verify(1L, List.of(10L, 11L));

			// then
			assertThat(result).isEqualTo(1L);
		}

		@Test
		void 다른_사용자가_점유한_좌석이_섞이면_0을_반환한다() {
			// given
			occupy(1L, List.of(10L));
			occupy(2L, List.of(11L));

			// when
			Long result = verify(1L, List.of(10L, 11L));

			// then
			assertThat(result).isZero();
		}

		@Test
		void 점유되지_않은_좌석이_섞이면_0을_반환한다() {
			// given
			occupy(1L, List.of(10L));

			// when
			Long result = verify(1L, List.of(10L, 99L));

			// then
			assertThat(result).isZero();
		}

		@Test
		void 점유가_만료되면_0을_반환한다() {
			// given
			occupy(1L, List.of(10L));
			redisUtil.delete(ScheduleSeatRedisKeys.occupyKey(10L));

			// when
			Long result = verify(1L, List.of(10L));

			// then
			assertThat(result).isZero();
		}
	}
}
