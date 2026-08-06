package ticketing.global.enums;

import java.time.Duration;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import ticketing.global.constants.CacheName;

@Getter
@RequiredArgsConstructor
public enum CacheGroup {

	CONCERT_DETAIL(
		CacheName.CONCERT_DETAIL,
		Duration.ofMinutes(10),
		CacheType.GLOBAL
	),
	CONCERT_SCHEDULE_DETAIL(
		CacheName.CONCERT_SCHEDULE_DETAIL,
		Duration.ofMinutes(10),
		CacheType.GLOBAL
	);

	private final String cacheName;
	private final Duration expiredAfterWrite;
	private final CacheType cacheType;

	private static final Map<String, CacheGroup> CACHE_MAP = Stream.of(values())
		.collect(Collectors.toUnmodifiableMap(
			CacheGroup::getCacheName,
			Function.identity()
		));

	private static CacheGroup get(String cacheName) {
		CacheGroup cacheGroup = CACHE_MAP.get(cacheName);
		if (cacheGroup == null) {
			throw new NoSuchElementException('\'' + cacheName + '\'' + " Cache Name Not Found");
		}
		return cacheGroup;
	}
}
