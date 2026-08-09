package ticketing.global.cache.manager;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ticketing.global.cache.wrapper.CacheWrapper;
import ticketing.global.util.JitterUtil;
import ticketing.global.cache.enums.CacheGroup;
import ticketing.global.cache.enums.CacheType;
import ticketing.global.util.RedisUtil;

@Component
@RequiredArgsConstructor
public class RedisCacheManager implements CacheManager {

	private final RedisUtil redisUtil;

	@Override
	public CacheType getSupportedType() {
		return CacheType.GLOBAL;
	}

	@Override
	public <T> CacheWrapper<T> get(CacheGroup group, String cacheKey) {
		return (CacheWrapper<T>) redisUtil.getObject(cacheKey);
	}

	@Override
	public void set(CacheGroup group, String cacheKey, CacheWrapper<?> value) {
		Duration ttl = JitterUtil.applyJitter(group.getExpiredAfterWriteGlobal());
		redisUtil.set(cacheKey, value, ttl);
	}

	@Override
	public Long getExpire(CacheGroup group, String cacheKey) {
		return redisUtil.getExpire(cacheKey, TimeUnit.MILLISECONDS);
	}

	@Override
	public void evict(CacheGroup group, String cacheKey) {
		redisUtil.delete(cacheKey);
	}
}
