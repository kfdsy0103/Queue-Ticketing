package ticketing.domain.order.order.service;

import java.time.Duration;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.order.order.dto.ConfirmDTO;
import ticketing.domain.order.order.exception.OrderErrorCode;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.util.RedisLockService;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFacadeService {

	private static final String LOCK_KEY_PREFIX = "order:lock:confirm:";
	private static final Duration LOCK_TTL = Duration.ofSeconds(5);

	private final OrderCommandService orderCommandService;
	private final RedisLockService redisLockService;

	public ConfirmDTO.Result confirm(ConfirmDTO.Command command) {

		String lockKey = LOCK_KEY_PREFIX + command.getOrderId();
		boolean acquired = redisLockService.tryLock(lockKey, command.getUserId().toString(), LOCK_TTL);

		if (!acquired) {
			throw new GeneralException(OrderErrorCode.ORDER_IN_PROGRESS);
		}

		try {
			return orderCommandService.confirm(command);
		} finally {
			redisLockService.releaseLock(lockKey);
		}
	}
}
