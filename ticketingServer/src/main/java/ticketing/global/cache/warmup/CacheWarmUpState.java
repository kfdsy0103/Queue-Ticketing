package ticketing.global.cache.warmup;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

@Component
public class CacheWarmUpState {
	private final AtomicBoolean warmUp = new AtomicBoolean(false);

	public boolean isWarmUp() {
		return warmUp.get();
	}
	public void warmUp() {warmUp.set(true);}
}
