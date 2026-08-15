package ticketing.global.config;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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

	/**
	 * 스케쥴러용 스레드풀
	 */
	@Bean(name = "schedulerTaskExecutor")
	public Executor schedulerTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("scheduler-worker-");
		executor.setRejectedExecutionHandler((task, exe) -> {
			log.error(
				"SchedulerTaskExecutor에서 Task rejected. active={}, pool={}, queue={}, completed={}",
				exe.getActiveCount(),
				exe.getPoolSize(),
				exe.getQueue().size(),
				exe.getCompletedTaskCount()
			);
			throw new RejectedExecutionException("SchedulerTaskExecutor 큐가 가득 차 작업을 처리할 수 없습니다.");
		});
		executor.initialize();
		return executor;
	}

	/**
	 * ShedLock을 위한 LockProvider 등록
	 */
	@Bean
	public LockProvider lockProvider(RedisConnectionFactory redisConnectionFactory) {
		return new RedisLockProvider(redisConnectionFactory, "ticketing");
	}
}
