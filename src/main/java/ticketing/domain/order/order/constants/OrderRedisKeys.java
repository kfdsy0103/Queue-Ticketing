package ticketing.domain.order.order.constants;

public final class OrderRedisKeys {

	private OrderRedisKeys() {}

	/**
	 * 주문 중복 생성 따닥키.
	 */
	public static String createLockKey(Long userId) {
		return "order:lock:create:" + userId;
	}

	/**
	 * 주문 확정 멱등키 및 클린업 스케쥴러(외부 API는 OK, 로컬은 Fail인 경우를 주기적 청소)와의 경합 방지
	 -*/
	public static String confirmLockKey(Long orderId) {
		return "order:lock:confirm:" + orderId;
	}
}
