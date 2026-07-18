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
	 * 사용자별 세션 정보 Hash. (field: userId, value: queueSessionId)
	 */
	public static String userInfoKey(Long concertScheduleId) {
		return waitingKey(concertScheduleId) + ":info";
	}

	/**
	 * 작업열(Active) 사용자 목록 Hash. (field: userId, value: queueSessionId, 필드별 TTL은 HEXPIRE로 관리, Redis 7.4+)
	 */
	public static String activeKey(Long concertScheduleId) {
		return waitingKey(concertScheduleId) + ":active";
	}
}
