package ticketing.domain.order.order.constants;

public final class OrderRedisKeys {

	private OrderRedisKeys() {
	}

	/**
	 * 주문 중복 생성 따닥키.
	 */
	public static String createLockKey(Long userId) {
		return "order:lock:create:" + userId;
	}

	/**
	 * confirm() + cancelAll() + 스케쥴러 작동 시 경합 방지 confirm 키
	 */
	public static String confirmLockKey(Long orderId) {
		return "order:lock:confirm:" + orderId;
	}
}
