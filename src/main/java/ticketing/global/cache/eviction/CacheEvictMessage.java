package ticketing.global.cache.eviction;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CacheEvictMessage {
	private String cacheName;	// 캐시 그룹명
	private String cacheKey;	// 대상 키
	private String instanceId;	// 발행한 인스턴스 식별
}
