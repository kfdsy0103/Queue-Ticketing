package queue.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.java21.instrument.binder.jdk.VirtualThreadMetrics;

@Configuration
public class MetricsConfig {

	@Bean
	public VirtualThreadMetrics virtualThreadMetrics() {
		return new VirtualThreadMetrics();
	}
}
