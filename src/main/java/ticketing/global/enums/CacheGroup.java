package ticketing.global.enums;

import java.time.Duration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.global.constants.CacheKeyPrefix;

@Getter
@RequiredArgsConstructor
public enum CacheGroup {

	CONCERT_DETAIL(
		CacheKeyPrefix.CONCERT_DETAIL,
		Duration.ofMinutes(1),
		CacheType.GLOBAL
	),
	CONCERT_SCHEDULE_DETAIL(
		CacheKeyPrefix.CONCERT_SCHEDULE_DETAIL,
		Duration.ofMinutes(1),
		CacheType.GLOBAL
	);

	private final String keyPrefix;
	private final Duration expiredAfterWrite;
	private final CacheType cacheType;
}
