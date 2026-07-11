package ticketing.domain.payment.service;

import java.time.Duration;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.payment.dto.ApproveDTO;
import ticketing.domain.payment.dto.ReadyDTO;
import ticketing.domain.payment.exception.PaymentErrorCode;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.util.RedisLockService;

/**
 * orderId 기준 락으로 approve()의 동시 중복 처리(따닥)를 막는 Facade입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFacadeService {

	private static final String LOCK_KEY_PREFIX = "payment:lock:order:";
	private static final Duration LOCK_TTL = Duration.ofSeconds(5);

	private final PaymentService paymentService;
	private final RedisLockService redisLockService;

	public ReadyDTO.Result ready(ReadyDTO.Command command) {
		return paymentService.ready(command);
	}

	public ApproveDTO.Result approve(ApproveDTO.Command command) {

		String lockKey = LOCK_KEY_PREFIX + command.getOrderId();
		boolean acquired = redisLockService.tryLock(lockKey, command.getUserId().toString(), LOCK_TTL);

		if (!acquired) {
			throw new GeneralException(PaymentErrorCode.PAYMENT_IN_PROGRESS);
		}

		try {
			return paymentService.approve(command);
		} finally {
			redisLockService.releaseLock(lockKey);
		}
	}
}
