package ticketing.global.cache.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CacheType {

	LOCAL("로컬 캐시만 적용"),
	GLOBAL("글로벌 캐시만 적용"),
	COMPOSITE("로컬 및 글로벌 캐시 적용");

	private final String description;
}
