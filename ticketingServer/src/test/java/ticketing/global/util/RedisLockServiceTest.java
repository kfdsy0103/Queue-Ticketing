package ticketing.global.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/**
 * 락 키 접두어, 대기·점유 시간 전달, 인터럽트 처리, 해제 조건을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class RedisLockServiceTest {

	private static final String KEY = "scheduleSeat:1";
	private static final String LOCK_KEY = "lock:scheduleSeat:1";
	private static final Duration LEASE = Duration.ofSeconds(3);
	private static final Duration WAIT_FOR = Duration.ofSeconds(1);

	@Mock
	private RedissonClient redissonClient;

	@Mock
	private RLock lock;

	@InjectMocks
	private RedisLockService redisLockService;

	@Test
	void 락을_획득하면_true를_반환하고_접두어가_붙은_키로_대기없이_시도한다() throws InterruptedException {
		// given
		given(redissonClient.getLock(LOCK_KEY)).willReturn(lock);
		given(lock.tryLock(0, LEASE.toMillis(), TimeUnit.MILLISECONDS)).willReturn(true);

		// when
		boolean acquired = redisLockService.tryLock(KEY, LEASE);

		// then
		assertThat(acquired).isTrue();
		verify(redissonClient).getLock(LOCK_KEY);
	}

	@Test
	void 이미_점유된_락이면_false를_반환한다() throws InterruptedException {
		// given
		given(redissonClient.getLock(LOCK_KEY)).willReturn(lock);
		given(lock.tryLock(0, LEASE.toMillis(), TimeUnit.MILLISECONDS)).willReturn(false);

		// when
		boolean acquired = redisLockService.tryLock(KEY, LEASE);

		// then
		assertThat(acquired).isFalse();
	}

	@Test
	void 대기_시간을_준_경우_해당_시간이_그대로_전달된다() throws InterruptedException {
		// given
		given(redissonClient.getLock(LOCK_KEY)).willReturn(lock);
		given(lock.tryLock(WAIT_FOR.toMillis(), LEASE.toMillis(), TimeUnit.MILLISECONDS)).willReturn(true);

		// when
		boolean acquired = redisLockService.tryLock(KEY, LEASE, WAIT_FOR);

		// then
		assertThat(acquired).isTrue();
		verify(lock).tryLock(WAIT_FOR.toMillis(), LEASE.toMillis(), TimeUnit.MILLISECONDS);
	}

	@Test
	void 대기_중_인터럽트되면_false를_반환하고_인터럽트_상태를_복원한다() throws InterruptedException {
		// given
		given(redissonClient.getLock(LOCK_KEY)).willReturn(lock);
		given(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).willThrow(new InterruptedException());

		// when
		boolean acquired = redisLockService.tryLock(KEY, LEASE, WAIT_FOR);

		// then
		assertThat(acquired).isFalse();
		// Thread.interrupted() 는 플래그를 읽으면서 지운다. 다음 테스트로 상태가 새지 않도록 여기서 정리한다
		assertThat(Thread.interrupted()).isTrue();
	}

	@Test
	void 현재_스레드가_쥐고_있는_락만_해제한다() {
		// given
		given(redissonClient.getLock(LOCK_KEY)).willReturn(lock);
		given(lock.isHeldByCurrentThread()).willReturn(true);

		// when
		redisLockService.releaseLock(KEY);

		// then
		verify(lock).unlock();
	}

	@Test
	void 다른_스레드가_쥔_락은_해제하지_않는다() {
		// given
		given(redissonClient.getLock(LOCK_KEY)).willReturn(lock);
		given(lock.isHeldByCurrentThread()).willReturn(false);

		// when
		redisLockService.releaseLock(KEY);

		// then
		verify(lock, never()).unlock();
	}
}
