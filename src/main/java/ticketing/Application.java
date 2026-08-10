package ticketing;

import java.time.LocalDateTime;
import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import ticketing.global.cache.warmup.CacheWarmUpState;

@EnableRetry
@EnableCaching
@EnableScheduling
@RestController
@RequiredArgsConstructor
@SpringBootApplication
public class Application {

	private final CacheWarmUpState cacheWarmUpState;

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@PostConstruct
	void init() {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
	}

	@GetMapping("/health")
	public ResponseEntity<String> health() {
		if (!cacheWarmUpState.isWarmUp()) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("로컬 캐시 워밍업 중입니다.");
		}
		return ResponseEntity.ok(LocalDateTime.now().toString());
	}
}
