package ticketing.global.cache.manager;

import ticketing.global.cache.wrapper.CacheWrapper;
import ticketing.global.cache.enums.CacheGroup;
import ticketing.global.cache.enums.CacheType;

public interface CacheManager {

	/**
	 * LOCAL or GLOBAL 타입별로 전략 작성, CacheService에서 캐시 유형에 따라 오케스트레이션하도록
	 */
	CacheType getSupportedType();

	/**
	 * 캐시 데이터를 조회합니다.
	 */
	<T> CacheWrapper<T> get(CacheGroup group, String cacheKey);

	/**
	 * 캐시 데이터를 적재합니다.
	 */
	void set(CacheGroup group, String cacheKey, CacheWrapper<?> value);

	/**
	 * 남은 TTL을 밀리초로 반환합니다.
	 */
	Long getExpire(CacheGroup group, String cacheKey);

	/**
	 * 캐시 엔트리 하나를 무효화합니다.
	 */
	void evict(CacheGroup group, String cacheKey);
}
