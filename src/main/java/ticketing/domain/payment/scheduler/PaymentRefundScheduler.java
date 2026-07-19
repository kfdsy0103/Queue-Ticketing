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
import ticketing.domain.payment.entity.Payment;
import ticketing.domain.payment.repository.PaymentRepository;
import ticketing.domain.payment.service.PaymentCommandService;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRefundScheduler {

	// 5분이 지나도 처리되지 못하고 (서버 크래시, 네트워크 문제 등 ..) 잔존해있는 Payment 조회 시 기준이 되는 시간
	private static final Duration STALE_READY_THRESHOLD = Duration.ofMinutes(5);

	private final PaymentRepository paymentRepository;
	private final PaymentCommandService paymentCommandService;

	/**
	 * PG 승인 이후 서버 크래시로 로컬에 반영되지 못한 결제 건을 PG 상태 재조회로 복구/환불하는 스케쥴러입니다.
	 * 		동작: 1분마다 READY 상태로 STALE_READY_THRESHOLD 이상 남아있는 Payment 건을 조회하고, 각 Payment의 PG 상태를 확인 후 확정 처리합니다.
	 * 		+ ShedLock으로 다중 인스턴스에서의 스케쥴러 동작 제어 추가
	 */
	@Async("schedulerTaskExecutor")
	@Scheduled(fixedDelay = 60000)
	@SchedulerLock(name = "paymentRefundScheduler", lockAtLeastFor = "PT10S", lockAtMostFor = "PT50S")
	public void refundOrphanedPayments() {

		// 조회 기준 시간
		LocalDateTime threshold = LocalDateTime.now().minus(STALE_READY_THRESHOLD);

		// 아직도 READY로 잔존해있는 STALE_READY_THRESHOLD 이상 지난 Payment건 조회
		List<Payment> stalePayments = paymentRepository.findAllByStatusAndCreatedAtBefore(
			Payment.PaymentStatus.READY,
			threshold
		);

		for (Payment payment : stalePayments) {
			try {
				paymentCommandService.resolveStalePayment(payment.getId());
			} catch (Exception e) {
				log.error("[PaymentRefundScheduler] - refundOrphanedPayments() paymentId={} 처리 중 오류", payment.getId(), e);
			}
		}
	}
}
