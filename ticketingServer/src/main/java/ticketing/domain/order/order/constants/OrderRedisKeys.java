package ticketing.domain.order.order.constants;

public final class OrderRedisKeys {

	private OrderRedisKeys() {
	}

	/**
	 * 주문 중복 생성 따닥키.
	 */
	public static String createLockKey(Long userId) {
		return "order:create:" + userId;
	}

	/**
	 * confirm()과 READY 대사 스케쥴러의 경합 방지 키. 둘 다 PENDING 주문의 READY 결제를 다룬다.
	 */
	public static String confirmLockKey(Long orderId) {
		return "order:confirm:" + orderId;
	}

	/**
	 * cancelAll()과 CANCEL_REQUESTED 대사 스케쥴러의 경합 방지 키. 둘 다 CONFIRMED 주문의 환불을 다룬다.
	 * confirm 키와 분리해도 되는 이유는 두 흐름이 요구하는 Order 상태(PENDING vs CONFIRMED)가 서로 배타적이기 때문이다.
	 */
	public static String cancelLockKey(Long orderId) {
		return "order:cancel:" + orderId;
	}
}
