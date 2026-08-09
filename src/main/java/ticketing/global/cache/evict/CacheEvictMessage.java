package ticketing.global.cache.evict;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CacheEvictMessage {
	private String cacheName;	// 캐시 그룹명
	private String cacheKey;	// 대상 키
}
