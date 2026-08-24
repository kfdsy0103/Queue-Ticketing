package ticketing.domain.order.order.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import ticketing.domain.order.order.service.OrderFacadeService;
import ticketing.domain.order.order.service.OrderQueryService;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

	// 10분 이상 결제 없이 PENDING으로 남아있는 Order 정리
	private static final Duration STALE_PENDING_THRESHOLD = Duration.ofMinutes(10);

	private final OrderQueryService orderQueryService;
	private final OrderFacadeService orderFacadeService;

	/**
	 * create()에서 PG 호출에 실패하여 PENDING으로 잔존해있는 Order건을 주기적으로 처리합니다.
	 */
	@Scheduled(cron = "${ticketing.scheduler.order.expire-orphaned-cron:0 * * * * *}")
	@SchedulerLock(name = "processOrphanedPendingOrder", lockAtLeastFor = "PT10S", lockAtMostFor = "PT50S")
	public void processOrphanedPendingOrders() {

		LocalDateTime threshold = LocalDateTime.now().minus(STALE_PENDING_THRESHOLD);

		List<Long> orphanedOrderIds = orderQueryService.findOrphanedPendingOrderIds(threshold);

		int expiredCount = 0;
		for (Long orderId : orphanedOrderIds) {
			try {
				if (orderFacadeService.expireOrphanedOrder(orderId)) {
					expiredCount++;
				}
			} catch (Exception e) {
				log.error("[OrderScheduler] - processOrphanedPendingOrders() orderId={} 처리 중 오류", orderId, e);
			}
		}

		if (expiredCount > 0) {
			log.info("[OrderScheduler] 결제 없는 PENDING 주문 {}건 만료 처리", expiredCount);
		}
	}
}
