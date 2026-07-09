package ticketing.domain.queue.constants;

public final class QueueRedisKeys {

	private QueueRedisKeys() {}

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
	 * 입장이 승격된 사용자 목록 Hash. (field: userId, TTL: 필드 단위 HEXPIRE Redis 7.4+)
	 */
	public static String activeKey(Long concertScheduleId) {
		return "active:concertSchedule:" + concertScheduleId;
	}
}
