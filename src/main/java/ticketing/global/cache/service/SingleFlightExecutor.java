package ticketing.global.cache.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 로컬 캐시 사용 -> 분산락 필요 x, 인스턴스 레벨에서 ConcurrentHashMap으로 처리
 */
@Slf4j
@Component
public class SingleFlightExecutor {

	private final ConcurrentHashMap<String, CompletableFuture<?>> inFlightRequests = new ConcurrentHashMap<>();

	public <V> V execute(String key, Supplier<V> supplier) {

		while (true) {

			CompletableFuture<V> existing = (CompletableFuture<V>) inFlightRequests.get(key);

			// 1. 이미 누군가 처리 중이면 리더에 합류
			if (existing != null) {
				try {
					return existing.join();
				} catch (Exception e) {
					throw e;
				}
			}

			// 2. 없는 경우 리더로 시도
			CompletableFuture<V> leader = new CompletableFuture<>();
			if (inFlightRequests.putIfAbsent(key, leader) != null) {
				continue;	// 이 사이 여러 스레드들이 인터리빙되게 코드 실행되었다면 putIfAbsent가 실패되므로 처음부터 합류하도록
			}

			// 3. 리더로 선정되었으므로 직접 조회
			try {
				V result = supplier.get();
				leader.complete(result);	// 조회 값 전파
				return result;
			} catch (RuntimeException e) {
				log.warn("리더 조회 실패 - key: {}", key, e);
				leader.completeExceptionally(e);	// 합류한 스레드들에게 Exception 전파
				throw e;
			} finally {
				inFlightRequests.remove(key, leader);
			}
		}
	}
}
