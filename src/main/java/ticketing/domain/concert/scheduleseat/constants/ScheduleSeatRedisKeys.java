package ticketing.domain.concert.scheduleseat.constants;

public final class ScheduleSeatRedisKeys {

	private ScheduleSeatRedisKeys() {}

	/**
	 * 좌석 점유(선점) 여부를 나타내는 Key. (value: 점유한 userId)
	 */
	public static String occupyKey(Long scheduleSeatId) {
		return "occupy:scheduleSeat:" + scheduleSeatId;
	}

	/**
	 * 사용자가 점유 중인 좌석의 역인덱스 Sorted Set. (member: scheduleSeatId, score: 점유 만료 시각 epoch millis)
	 */
	public static String userOccupyKey(Long concertScheduleId, Long userId) {
		return "occupy:user:" + concertScheduleId + ":" + userId;
	}

}
