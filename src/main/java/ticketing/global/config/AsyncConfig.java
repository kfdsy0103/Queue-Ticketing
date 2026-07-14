package ticketing.global.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.RequiredArgsConstructor;
import ticketing.global.mdc.MDCTaskDecorator;

@EnableAsync
@Configuration
@RequiredArgsConstructor
public class AsyncConfig {

	private final MDCTaskDecorator mdcTaskDecorator;

	@Bean(name = "queueTaskExecutor")
	public Executor queueTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("queue-worker-");
		executor.setTaskDecorator(mdcTaskDecorator);
		executor.initialize();
		return executor;
	}
}
