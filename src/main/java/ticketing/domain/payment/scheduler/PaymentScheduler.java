package ticketing.domain.payment.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import ticketing.domain.order.order.constants.OrderRedisKeys;
import ticketing.domain.payment.entity.Payment;
import ticketing.domain.payment.repository.PaymentRepository;
import ticketing.domain.payment.service.PaymentCommandService;
import ticketing.global.util.RedisLockService;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentScheduler {

	// 5분이 지나도 처리되지 못하고 (서버 크래시, 네트워크 문제 등 ..) 잔존해있는 Payment 조회 시 기준이 되는 시간
	private static final Duration STALE_READY_THRESHOLD = Duration.ofMinutes(5);
	// 환불 요청을 했는데 네트워크나 크래시 등으로 인해, CANCEL_REQUESTED 상태로 잔존해있는 Payment 조회 기준 시간
	private static final Duration STALE_CANCEL_THRESHOLD = Duration.ofMinutes(1);

	private static final Duration LOCK_TTL = Duration.ofSeconds(5);

	private final PaymentRepository paymentRepository;
	private final PaymentCommandService paymentCommandService;
	private final RedisLockService redisLockService;

	/**
	 * PG 승인 이후 서버 크래시로 로컬에 반영되지 못한 결제 건을 PG 상태 재조회로 복구/환불하는 스케쥴러입니다.
	 * 		동작: 1분마다 READY 상태로 STALE_READY_THRESHOLD 이상 남아있는 Payment 건을 조회하고, 각 Payment의 PG 상태를 확인 후 확정 처리합니다.
	 * 		+ ShedLock으로 다중 인스턴스에서의 스케쥴러 동작 제어 추가
	 */
	@Async("schedulerTaskExecutor")
	@Scheduled(fixedDelay = 60000)
	@SchedulerLock(name = "processReadyPayments", lockAtLeastFor = "PT10S", lockAtMostFor = "PT50S")
	public void processReadyPayments() {

		// 조회 기준 시간
		LocalDateTime threshold = LocalDateTime.now().minus(STALE_READY_THRESHOLD);

		// 아직도 READY로 잔존해있는 STALE_READY_THRESHOLD 이상 지난 Payment건 조회
		List<Payment> stalePayments = paymentRepository.findAllByStatusAndCreatedAtBefore(
			Payment.PaymentStatus.READY,
			threshold
		);

		for (Payment payment : stalePayments) {

			String lockKey = OrderRedisKeys.confirmLockKey(payment.getOrder().getId());
			if (!redisLockService.tryLock(lockKey, LOCK_TTL)) {
				continue;	// confirm()과 경합이 났음을 의미
			}

			try {
				paymentCommandService.processReadyPayment(payment.getId());
			} catch (Exception e) {
				log.error("[PaymentScheduler] - refundOrphanedPayments() paymentId={} 처리 중 오류", payment.getId(), e);
			} finally {
				redisLockService.releaseLock(lockKey);
			}
		}
	}

	/**
	 * PG 환불 요청 이후 서버 크래시 등으로 로컬에 반영되지 못한 취소 건을 마무리하는 스케쥴러입니다.
	 * 		동작: 1분마다 CANCEL_REQUESTED로 STALE_CANCEL_THRESHOLD 이상 남아있는 Payment 건을 조회하고, PG사에 상태를 재확인해 환불을 재요청을 결정하고 로컬 반영합니다.
	 */
	@Async("schedulerTaskExecutor")
	@Scheduled(fixedDelay = 60000)
	@SchedulerLock(name = "processCancelRequestedPayments", lockAtLeastFor = "PT10S", lockAtMostFor = "PT50S")
	public void processCancelRequestedPayments() {

		LocalDateTime threshold = LocalDateTime.now().minus(STALE_CANCEL_THRESHOLD);

		List<Payment> staleCancels = paymentRepository.findAllByStatusAndUpdatedAtBefore(
			Payment.PaymentStatus.CANCEL_REQUESTED,
			threshold
		);

		for (Payment payment : staleCancels) {

			String lockKey = OrderRedisKeys.confirmLockKey(payment.getOrder().getId());
			if (!redisLockService.tryLock(lockKey, LOCK_TTL)) {
				continue;	// cancelAll()과 경합이 났음을 의미
			}

			try {
				paymentCommandService.processCancelRequestedPayment(payment.getId());
			} catch (Exception e) {
				log.error("[PaymentScheduler] - processCancelRequestedPayments() paymentId={} 처리 중 오류", payment.getId(), e);
			} finally {
				redisLockService.releaseLock(lockKey);
			}
		}
	}
}
