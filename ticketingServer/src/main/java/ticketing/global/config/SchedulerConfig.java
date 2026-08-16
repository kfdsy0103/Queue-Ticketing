package ticketing.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S") 	// ISO 8601 표준 표기법
public class SchedulerConfig {

	// @Scheduled 메서드 수. 기본 TaskScheduler는 스레드가 1개라 주기가 짧은 잡이 나머지를 막는다.
	// @Async로 우회하면 ShedLock이 실제 실행을 감싸지 못할 수 있으므로, 스케쥴러 풀 자체를 늘린다.
	private static final int SCHEDULER_POOL_SIZE = 5;

	/**
	 * @Scheduled 전용 스케쥴러 풀
	 */
	@Bean
	public TaskScheduler taskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(SCHEDULER_POOL_SIZE);
		scheduler.setThreadNamePrefix("scheduler-worker-");
		scheduler.setErrorHandler(throwable ->
			log.error("[SchedulerConfig] 스케쥴러 작업에서 처리되지 못한 예외가 발생했습니다.", throwable)
		);
		scheduler.setWaitForTasksToCompleteOnShutdown(true);
		scheduler.setAwaitTerminationSeconds(60);
		scheduler.initialize();
		return scheduler;
	}

	/**
	 * ShedLock을 위한 LockProvider 등록
	 */
	@Bean
	public LockProvider lockProvider(RedisConnectionFactory redisConnectionFactory) {
		return new RedisLockProvider(redisConnectionFactory, "ticketing");
	}
}
