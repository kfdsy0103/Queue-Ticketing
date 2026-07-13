package ticketing.domain.concert.scheduleseat.constants;

public final class ScheduleSeatRedisKeys {

	private ScheduleSeatRedisKeys() {}

	/**
	 * 좌석 점유(선점) 여부를 나타내는 Key. (value: 점유한 userId, TTL로 자동 해제)
	 */
	public static String occupyKey(Long scheduleSeatId) {
		return "occupy:scheduleSeat:" + scheduleSeatId;
	}
}
