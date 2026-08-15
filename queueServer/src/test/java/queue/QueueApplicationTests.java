package queue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import queue.config.RedisTestContainersConfig;

@ActiveProfiles("test")
@SpringBootTest
class QueueApplicationTests extends RedisTestContainersConfig {

	/**
	 * DB 없이 Redis만으로 컨텍스트가 뜨는지 검증합니다.
	 * 대기열 서버 분리의 핵심 조건이므로, 여기서 실패하면 DB 의존이 되살아난 것입니다.
	 */
	@Test
	void contextLoads() {
	}

}
