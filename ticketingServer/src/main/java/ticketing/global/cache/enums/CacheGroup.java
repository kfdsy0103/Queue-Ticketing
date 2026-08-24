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
	),
	SCHEDULE_SEAT_LAYOUT(
		CacheName.SCHEDULE_SEAT_LAYOUT,
		CacheType.COMPOSITE,
		Duration.ofSeconds(10),
		Duration.ofSeconds(3),
		1000L
	);

	private final String cacheName;
	private final CacheType cacheType;
	private final Duration expiredAfterWriteGlobal;
	private final Duration expiredAfterWriteLocal;
	private final long maximumSize;

	public boolean useLocalCache() {
		return (this.cacheType.equals(CacheType.LOCAL) || this.cacheType.equals(CacheType.COMPOSITE));
	}

	public static CacheGroup fromCacheName(String cacheName) {
		for (CacheGroup group : values()) {
			if (group.cacheName.equals(cacheName)) {
				return group;
			}
		}
		return null;
	}
}
