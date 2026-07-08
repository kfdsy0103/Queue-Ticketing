package ticketing.global.util;

import java.util.concurrent.ThreadLocalRandom;

public class JitterUtil {

	// 처리량 별 티어
	private static final long LONG_WAIT_THRESHOLD = 10000;
	private static final long MEDIUM_WAIT_THRESHOLD = 1000;
	private static final long SHORT_WAIT_THRESHOLD = 100;

	// 티어별 Base 주기
	private static final long LONG_WAIT_BASE_INTERVAL_MS = 5000;
	private static final long MEDIUM_WAIT_BASE_INTERVAL_MS = 3000;
	private static final long SHORT_WAIT_BASE_INTERVAL_MS = 1000;
	private static final long IMMINENT_BASE_INTERVAL_MS = 500;

	private JitterUtil() {}

	/**
	 * 대기 순번(rank)에 따라 Base 폴링 간격이 정해지고, Jitter를 적용하여 최종 폴링 주기를 반환합니다. (ms 단위)
	 */
	public static long nextPollIntervalMillis(long rank) {
		long baseInterval = resolveBaseInterval(rank);
		return applyEqualJitter(baseInterval);
	}

	private static long resolveBaseInterval(long rank) {
		if (rank > LONG_WAIT_THRESHOLD) {
			return LONG_WAIT_BASE_INTERVAL_MS;
		}
		if (rank > MEDIUM_WAIT_THRESHOLD) {
			return MEDIUM_WAIT_BASE_INTERVAL_MS;
		}
		if (rank > SHORT_WAIT_THRESHOLD) {
			return SHORT_WAIT_BASE_INTERVAL_MS;
		}
		return IMMINENT_BASE_INTERVAL_MS;
	}

	private static long applyEqualJitter(long baseInterval) {
		long half = baseInterval / 2;
		return half + ThreadLocalRandom.current().nextLong(half + 1);
	}
}
