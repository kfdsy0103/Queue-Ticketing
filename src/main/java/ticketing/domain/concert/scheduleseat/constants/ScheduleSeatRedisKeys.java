package ticketing.domain.concert.scheduleseat.constants;

public final class ScheduleSeatRedisKeys {

	private ScheduleSeatRedisKeys() {}

	/**
	 * 좌석 점유(선점) 여부를 나타내는 Key. (value: 점유한 userId)
	 * 최초 occupy() 점유 시 5분 TTL로 시작하고, 찰나의 race condition을 막기 위해 주문 craete() 시 TTL이 한번 연장된다.
	 */
	public static String occupyKey(Long scheduleSeatId) {
		return "occupy:scheduleSeat:" + scheduleSeatId;
	}
}
