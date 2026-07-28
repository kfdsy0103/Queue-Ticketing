package ticketing.domain.order.order.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import ticketing.domain.order.order.entity.Order;
import ticketing.domain.order.order.repository.OrderRepository;
import ticketing.domain.order.order.service.OrderCommandService;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCleanupScheduler {

	// 10분 이상 결제 없이 PENDING으로 남아있는 Order 정리
	private static final Duration STALE_PENDING_THRESHOLD = Duration.ofMinutes(10);

	private final OrderRepository orderRepository;
	private final OrderCommandService orderCommandService;

	/**
	 * create()에서 Order는 저장됐지만 PG ready()/Payment 저장 전에 실패(크래시 등)하여
	 * 결제가 붙지 않은 채 남은 PENDING 주문을 1분마다 조회하여 취소 처리합니다.
	 */
	@Async("schedulerTaskExecutor")
	@Scheduled(fixedDelay = 60000)
	@SchedulerLock(name = "orderCleanupScheduler", lockAtLeastFor = "PT10S", lockAtMostFor = "PT50S")
	public void cleanupOrphanedPendingOrders() {

		LocalDateTime threshold = LocalDateTime.now().minus(STALE_PENDING_THRESHOLD);

		List<Order> orphanedOrders = orderRepository.findStalePendingOrdersWithoutPayment(
			Order.OrderStatus.PENDING,
			threshold
		);

		int cancelledCount = 0;
		for (Order order : orphanedOrders) {
			try {
				if (orderCommandService.cancelPendingOrder(order.getId())) {
					cancelledCount++;
				}
			} catch (Exception e) {
				log.error("[OrderCleanupScheduler] - cleanupOrphanedPendingOrders() orderId={} 처리 중 오류", order.getId(), e);
			}
		}

		if (cancelledCount > 0) {
			log.info("[OrderCleanupScheduler] 결제 없는 PENDING 주문 {}건 취소 처리", cancelledCount);
		}
	}
}
