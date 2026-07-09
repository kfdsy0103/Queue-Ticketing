package ticketing.domain.queue.constants;

public final class QueueRedisKeys {

	private QueueRedisKeys() {}

	/**
	 * 대기 중인 사용자가 있는 콘서트 회차 목록 (Set).
	 */
	public static final String ACTIVE_SCHEDULES_KEY = "queue:activeSchedules";

	/**
	 * 대기열 Sorted Set. (member: userId, score: 순번)
	 */
	public static String waitingKey(Long concertScheduleId) {
		return "queue:concertSchedule:" + concertScheduleId;
	}

	/**
	 * 순번 발급용 카운터.
	 */
	public static String counterKey(Long concertScheduleId) {
		return waitingKey(concertScheduleId) + ":counter";
	}

	/**
	 * 사용자별로 대기열을 소유한 화면(기기)을 기록하는 Hash.
	 */
	public static String sessionKey(Long concertScheduleId) {
		return waitingKey(concertScheduleId) + ":session";
	}

	/**
	 * 입장이 승격된 사용자 목록 Sorted Set. (member: userId, score: 만료 시각(epoch millis))
	 */
	public static String activeKey(Long concertScheduleId) {
		return "active:concertSchedule:" + concertScheduleId;
	}
}
