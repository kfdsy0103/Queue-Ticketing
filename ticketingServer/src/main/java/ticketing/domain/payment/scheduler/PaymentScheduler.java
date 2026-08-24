package ticketing.domain.payment.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import ticketing.domain.order.order.exception.OrderErrorCode;
import ticketing.domain.payment.service.PaymentFacadeService;
import ticketing.domain.payment.service.PaymentQueryService;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentScheduler {

	// 처리되지 못하고 (서버 크래시, 네트워크 문제 등 ..) 잔존해있는 Payment 조회 시 기준이 되는 시간. Redis 점유 TTL(5분) 보다 길게 설정
	private static final Duration STALE_READY_THRESHOLD = Duration.ofMinutes(10);
	// 환불 요청을 했는데 네트워크나 크래시 등으로 인해, CANCEL_REQUESTED 상태로 잔존해있는 Payment 조회 기준 시간
	private static final Duration STALE_CANCEL_THRESHOLD = Duration.ofMinutes(5);

	private final PaymentQueryService paymentQueryService;
	private final PaymentFacadeService paymentFacadeService;

	/**
	 * PG 승인 이후 서버 크래시로 로컬에 반영되지 못한 결제 건을 PG 상태 재조회로 복구/환불하는 스케쥴러입니다.
	 * 		동작: 1분마다 READY 상태로 STALE_READY_THRESHOLD 이상 남아있는 Payment 건을 조회하고, 각 건의 PG 상태를 확인 후 확정 처리합니다.
	 * 		+ ShedLock으로 다중 인스턴스에서의 스케쥴러 동작 제어 추가
	 */
	@Scheduled(cron = "${ticketing.scheduler.payment.reconcile-ready-cron:0 * * * * *}")
	@SchedulerLock(name = "reconcileReadyPayments", lockAtLeastFor = "PT10S", lockAtMostFor = "PT50S")
	public void reconcileReadyPayments() {

		// 조회 기준 시간
		LocalDateTime threshold = LocalDateTime.now().minus(STALE_READY_THRESHOLD);

		// 임계 시간이 지난 Payment 건들만 조회
		List<Long> staleOrderIds = paymentQueryService.findStaleReadyOrderIds(threshold);

		for (Long orderId : staleOrderIds) {
			try {
				paymentFacadeService.reconcileReadyPayment(orderId);
			} catch (Exception e) {
				log.error("[PaymentScheduler] - reconcileReadyPayments() orderId={} 처리 중 오류", orderId, e);
			}
		}
	}

	/**
	 * PG 환불 요청 이후 서버 크래시 등으로 로컬에 반영되지 못한 취소 건을 마무리하는 스케쥴러입니다.
	 */
	@Scheduled(cron = "${ticketing.scheduler.payment.reconcile-cancel-requested-cron:0 * * * * *}")
	@SchedulerLock(name = "reconcileCancelRequestedPayments", lockAtLeastFor = "PT10S", lockAtMostFor = "PT50S")
	public void reconcileCancelRequestedPayments() {

		LocalDateTime threshold = LocalDateTime.now().minus(STALE_CANCEL_THRESHOLD);

		List<Long> staleOrderIds = paymentQueryService.findStaleCancelRequestedOrderIds(threshold);

		for (Long orderId : staleOrderIds) {
			try {
				paymentFacadeService.reconcileCancelRequestedPayment(orderId);
			} catch (Exception e) {
				log.error("[PaymentScheduler] - reconcileCancelRequestedPayments() orderId={} 처리 중 오류", orderId, e);
			}
		}
	}
}
