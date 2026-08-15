package ticketing.global.config;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.global.mdc.MDCTaskDecorator;

@Slf4j
@EnableAsync
@Configuration
@RequiredArgsConstructor
public class AsyncConfig {

	private final MDCTaskDecorator mdcTaskDecorator;

	/**
	 * 일반적인 @Async 용도의 기본 스레드 풀
	 */
	@Bean(name = "taskExecutor")
	public Executor taskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("async-worker-");
		executor.setTaskDecorator(mdcTaskDecorator);
		executor.setRejectedExecutionHandler((task, exe) -> {
			log.error(
				"TaskExecutor에서 Task rejected. active={}, pool={}, queue={}, completed={}",
				exe.getActiveCount(),
				exe.getPoolSize(),
				exe.getQueue().size(),
				exe.getCompletedTaskCount()
			);
			throw new RejectedExecutionException("TaskExecutor 큐가 가득 차 작업을 처리할 수 없습니다.");
		});
		executor.initialize();
		return executor;
	}

	/**
	 * 캐시 갱신용 스레드 풀
	 */
	@Bean(name = "cacheRefreshExecutor")
	public Executor cacheRefreshExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(30);
		executor.setThreadNamePrefix("cache-refresh-");
		executor.setTaskDecorator(mdcTaskDecorator);
		executor.setRejectedExecutionHandler((task, exe) -> {
			log.warn(
				"캐시 갱신 큐가 가득 찼습니다. active={}, pool={}, queue={}, completed={}",
				exe.getActiveCount(),
				exe.getPoolSize(),
				exe.getQueue().size(),
				exe.getCompletedTaskCount()
			);
			throw new RejectedExecutionException("캐시 갱신 큐가 가득 차 작업을 처리할 수 없습니다.");
		});
		executor.initialize();
		return executor;
	}
}
