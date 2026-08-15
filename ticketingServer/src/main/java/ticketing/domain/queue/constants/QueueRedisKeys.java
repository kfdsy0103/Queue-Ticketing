package ticketing.domain.queue.constants;

/**
 * 대기열 서버와 공유하는 Redis 키 규칙.
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
	 * 사용자별 세션 정보 Key 접두어. Lua 스크립트에서 뒤에 userId를 붙여 사용합니다.
	 */
	public static String userInfoKeyPrefix(Long concertScheduleId) {
		return waitingKey(concertScheduleId) + ":info:";
	}

	/**
	 * 작업열(Active) 사용자 Key 접두어. Lua 스크립트에서 뒤에 userId를 붙여 사용합니다.
	 */
	public static String activeKeyPrefix(Long concertScheduleId) {
		return waitingKey(concertScheduleId) + ":active:";
	}

	/**
	 * 작업열(Active) 사용자별 String Key. (value: queueSessionId, TTL은 EXPIRE로 관리)
	 */
	public static String activeKey(Long concertScheduleId, Long userId) {
		return activeKeyPrefix(concertScheduleId) + userId;
	}
}
