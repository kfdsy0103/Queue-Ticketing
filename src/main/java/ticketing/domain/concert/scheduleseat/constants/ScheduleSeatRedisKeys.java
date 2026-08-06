package ticketing.domain.concert.scheduleseat.constants;

public final class ScheduleSeatRedisKeys {

	private ScheduleSeatRedisKeys() {}

	/**
	 * 좌석 점유(선점) 여부를 나타내는 Key. (value: 점유한 userId)
	 * 좌석 단위 소유권의 진실은 이 Key에 있고, 아래 두 인덱스는 조회를 위한 색인이다.
	 */
	public static String occupyKey(Long scheduleSeatId) {
		return "occupy:scheduleSeat:" + scheduleSeatId;
	}

	/**
	 * 사용자가 점유 중인 좌석의 역인덱스 Sorted Set. (member: scheduleSeatId, score: 점유 만료 시각 epoch millis)
	 * 회차를 가리지 않고 한 번에 조회할 수 있도록 Key에 회차를 넣지 않는다. 좌석이 속한 회차는 DB에서 얻는다.
	 */
	public static String userOccupyKey(Long userId) {
		return "occupy:user:" + userId;
	}

	/**
	 * 회차에서 점유 중인 좌석의 인덱스 Sorted Set. (member: scheduleSeatId, score: 점유 만료 시각 epoch millis)
	 * 좌석 배치도 조회가 좌석마다 점유 여부를 묻는 대신, 점유된 좌석만 한 번에 받기 위해 사용한다.
	 */
	public static String scheduleOccupyKey(Long concertScheduleId) {
		return "occupy:schedule:" + concertScheduleId;
	}
}
