package ticketing.global.config;

import java.time.Duration;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class RedisConfig {

	@Value("${spring.data.redis.host}")
	private String host;
	@Value("${spring.data.redis.port}")
	private int port;

	@Bean
	public RedissonClient redissonClient() {
		Config config = new Config();
		config.useSingleServer()
			.setAddress("redis://" + host + ":" + port)
			.setTimeout(10000);
		return Redisson.create(config);
	}

	/**
	 * Redisson 기반 커넥션 팩토리 빈
	 */
	@Bean
	public RedisConnectionFactory redisConnectionFactory(RedissonClient redissonClient) {
		return new RedissonConnectionFactory(redissonClient);
	}

	@Bean
	public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, Object> template = new RedisTemplate<>();

		template.setConnectionFactory(connectionFactory);
		template.setKeySerializer(new StringRedisSerializer());
		template.setHashKeySerializer(new StringRedisSerializer());
		template.setValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper()));
		template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper()));
		template.afterPropertiesSet();

		return template;
	}

	@Bean
	public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

		// Default cache 설정
		RedisCacheConfiguration redisCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
			.entryTtl(Duration.ofMinutes(5))
			.disableCachingNullValues()
			.prefixCacheNameWith("cache:") // @Cacheable로 저장하는 경우 key 접두어
			.serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
			.serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper())));

		return RedisCacheManager.builder(connectionFactory)
			.cacheDefaults(redisCacheConfiguration)
			.build();
	}

	private ObjectMapper objectMapper() {
		PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
			.allowIfSubType(Object.class)
			.build();

		return JsonMapper.builder()
			.disable(SerializationFeature.INDENT_OUTPUT)
			.activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.NON_FINAL)
			.build();
	}
}
