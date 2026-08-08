package ticketing.global.enums;

import java.time.Duration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.global.constants.CacheName;

@Getter
@RequiredArgsConstructor
public enum CacheGroup {

	CONCERT_DETAIL(
		CacheName.CONCERT_DETAIL,
		Duration.ofMinutes(1),
		CacheType.GLOBAL
	),
	CONCERT_SCHEDULE_DETAIL(
		CacheName.CONCERT_SCHEDULE_DETAIL,
		Duration.ofMinutes(1),
		CacheType.GLOBAL
	);

	private final String cacheName;
	private final Duration expiredAfterWrite;
	private final CacheType cacheType;
}
