package ticketing.global.util;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public class JitterUtil {

	private static final double JITTER_RATIO = 0.1;

	private JitterUtil() {}

	/**
	 * TTL 10% 범위의 지터를 적용합니다.
	 */
	public static Duration applyJitter(Duration ttl) {
		long baseMillis = ttl.toMillis();
		long jitterRange = (long) (baseMillis * JITTER_RATIO);
		long jitterMillis = ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1);
		return Duration.ofMillis(baseMillis + jitterMillis);
	}
}
