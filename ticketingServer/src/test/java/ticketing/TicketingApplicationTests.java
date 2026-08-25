package ticketing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import ticketing.config.RedisTestContainersConfig;

@SpringBootTest
@ActiveProfiles("test")
class TicketingApplicationTests extends RedisTestContainersConfig {

	@Test
	void contextLoads() {
	}

}
