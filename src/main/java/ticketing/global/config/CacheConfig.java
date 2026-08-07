package ticketing.global.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.RequiredArgsConstructor;
import ticketing.global.enums.CacheGroup;
import ticketing.global.enums.CacheType;
import ticketing.global.util.JitterUtil;

@EnableCaching
@Configuration
@RequiredArgsConstructor
public class CacheConfig {

	@Bean
	public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

		// Default 캐시에 대한 설정
		RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
			.entryTtl(Duration.ofMinutes(5))
			.disableCachingNullValues()
			.prefixCacheNameWith("cache:") // @Cacheable로 저장하는 경우 key 접두어
			.serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
			.serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper())));

		// 정의된 캐시에 대한 설정
		Map<String, RedisCacheConfiguration> customConfig = Arrays.stream(CacheGroup.values())
			.filter(group -> group.getCacheType() == CacheType.GLOBAL)
			.collect(Collectors.toMap(
				CacheGroup::getCacheName,
				group -> defaultConfig.entryTtl(JitteredTtlFunction.of(group.getExpiredAfterWrite())) // 지터 적용 TTL
			));

		return RedisCacheManager.builder(connectionFactory)
			.transactionAware()	// 캐시 관련 처리는 @Transaction 내부 X -> afterCommit 시점에 일어나도록 함
			.cacheDefaults(defaultConfig)
			.withInitialCacheConfigurations(customConfig)
			.build();
	}

	private ObjectMapper objectMapper() {
		PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
			.allowIfSubType(Object.class)
			.build();

		return JsonMapper.builder()
			.addModule(new JavaTimeModule())	// LocalDate, LocalDateTime
			.disable(SerializationFeature.INDENT_OUTPUT)
			.activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.NON_FINAL)
			.build();
	}

	private static class JitteredTtlFunction implements RedisCacheWriter.TtlFunction {

		private final Duration baseTtl;

		private JitteredTtlFunction(Duration baseTtl) {
			this.baseTtl = baseTtl;
		}

		public static JitteredTtlFunction of(Duration baseTtl) {
			return new JitteredTtlFunction(baseTtl);
		}

		@Override
		public Duration getTimeToLive(Object key, Object value) {
			return JitterUtil.applyJitter(baseTtl);
		}
	}
}
