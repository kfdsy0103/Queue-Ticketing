package ticketing.global.cache.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CacheEntry<T> {
	private T cachedData;			// 실제 캐싱 데이터
	private long timeToCompute;		// 가져오는 데 걸린 시간 (ms)
}
