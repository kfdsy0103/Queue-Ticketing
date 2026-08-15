package ticketing.global.cache.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CacheResult<T> {
	private CacheEntry<T> entry;		// 캐싱 데이터 엔트리
	private String metricResult;		// 캐시 히트 or 미스 결과 (local_hit or global_hit or miss)
}
