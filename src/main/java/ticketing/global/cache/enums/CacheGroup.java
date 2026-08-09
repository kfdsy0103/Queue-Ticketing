package ticketing.global.cache.enums;

import java.time.Duration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.global.cache.constants.CacheName;

@Getter
@RequiredArgsConstructor
public enum CacheGroup {

	CONCERT_DETAIL(
		CacheName.CONCERT_DETAIL,
		CacheType.COMPOSITE,
		Duration.ofMinutes(1),
		Duration.ofSeconds(10),
		1000L
	),
	CONCERT_SCHEDULE_DETAIL(
		CacheName.CONCERT_SCHEDULE_DETAIL,
		CacheType.COMPOSITE,
		Duration.ofMinutes(1),
		Duration.ofSeconds(10),
		1000L
	);

	private final String cacheName;
	private final CacheType cacheType;
	private final Duration expiredAfterWriteGlobal;
	private final Duration expiredAfterWriteLocal;
	private final long maximumSize;
}
