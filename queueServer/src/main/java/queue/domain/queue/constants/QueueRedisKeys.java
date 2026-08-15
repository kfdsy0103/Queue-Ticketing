package queue.domain.queue.constants;

/**
 * 티켓팅 서버와 공유하는 Redis 키 규칙
 */
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
	 * 사용자별 세션 정보 Key 접두어.
	 */
	private static String userInfoKeyPrefix(Long concertScheduleId) {
		return waitingKey(concertScheduleId) + ":info:";
	}

	/**
	 * 사용자별 세션 정보 String Key. (value: queueSessionId, TTL은 EXPIRE로 관리)
	 */
	public static String userInfoKey(Long concertScheduleId, Long userId) {
		return userInfoKeyPrefix(concertScheduleId) + userId;
	}

	/**
	 * 작업열(Active) 사용자 Key 접두어.
	 */
	private static String activeKeyPrefix(Long concertScheduleId) {
		return waitingKey(concertScheduleId) + ":active:";
	}

	/**
	 * 작업열(Active) 사용자별 String Key. (value: queueSessionId, TTL은 EXPIRE로 관리)
	 */
	public static String activeKey(Long concertScheduleId, Long userId) {
		return activeKeyPrefix(concertScheduleId) + userId;
	}
}
