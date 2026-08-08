package ticketing.global.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * timeToCompute를 실제 데이터와 함께 보관하는 캐시 래퍼
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CacheWrapper<T> {
	private T cachedData;			// 실제 캐싱 데이터
	private long timeToCompute;		// 가져오는 데 걸린 시간 (ms)
}
