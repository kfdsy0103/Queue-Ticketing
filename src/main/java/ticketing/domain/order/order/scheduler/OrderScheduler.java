package ticketing.domain.order.order.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import ticketing.domain.order.order.constants.OrderRedisKeys;
import ticketing.domain.order.order.entity.Order;
import ticketing.domain.order.order.repository.OrderRepository;
import ticketing.domain.order.order.service.OrderCommandService;
import ticketing.global.util.RedisLockService;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true")	// api 서버 전용
@RequiredArgsConstructor
public class OrderScheduler {

	// 10분 이상 결제 없이 PENDING으로 남아있는 Order 정리
	private static final Duration STALE_PENDING_THRESHOLD = Duration.ofMinutes(10);

	// confirm()과 동일한 Lock 키, confirm 처리 중인 주문에 경합되지 않도록
	private static final Duration LOCK_TTL = Duration.ofSeconds(5);

	private final OrderRepository orderRepository;
	private final OrderCommandService orderCommandService;
	private final RedisLockService redisLockService;

	/**
	 * create()에서 Order는 PG 호출에 실패하여 PENDING으로 잔존해있는 Order건을 주기적으로 처리합니다.
	 */
	@Async("schedulerTaskExecutor")
	@Scheduled(fixedDelay = 60000)
	@SchedulerLock(name = "processOrphanedPendingOrder", lockAtLeastFor = "PT10S", lockAtMostFor = "PT50S")
	public void processOrphanedPendingOrders() {

		LocalDateTime threshold = LocalDateTime.now().minus(STALE_PENDING_THRESHOLD);

		List<Order> orphanedPendingOrders = orderRepository.findOrphanedPendingOrdersWithoutPayment(
			Order.OrderStatus.PENDING,
			threshold
		);

		int expiredCount = 0;
		for (Order order : orphanedPendingOrders) {

			String lockKey = OrderRedisKeys.confirmLockKey(order.getId());
			if (!redisLockService.tryLock(lockKey, LOCK_TTL)) {
				continue;	// confirm()과 경합된 상황
			}

			try {
				if (orderCommandService.expireOrder(order.getId())) {
					expiredCount++;
				}
			} catch (Exception e) {
				log.error("[OrderScheduler] - cleanupOrphanedPendingOrders() orderId={} 처리 중 오류", order.getId(), e);
			} finally {
				redisLockService.releaseLock(lockKey);
			}
		}

		if (expiredCount > 0) {
			log.info("[OrderScheduler] 결제 없는 PENDING 주문 {}건 만료 처리", expiredCount);
		}
	}
}
