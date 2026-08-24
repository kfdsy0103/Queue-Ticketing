package ticketing.domain.concert.concert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ticketing.config.RedisTestContainersConfig;
import ticketing.domain.concert.concert.dto.FindDTO;
import ticketing.domain.concert.concert.entity.Concert;
import ticketing.domain.concert.concert.exception.ConcertErrorCode;
import ticketing.domain.concert.concert.repository.ConcertRepository;
import ticketing.domain.venue.venue.entity.Venue;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.cache.enums.CacheGroup;
import ticketing.global.cache.manager.CaffeineCacheStore;

/**
 * ConcertQueryService.find()에 붙은 @Cacheable이 실제로 TieredCacheManager를 통해
 * 배선되는지(캐시 히트 시 리포지토리 미호출, NOT_FOUND 예외 보존, 유일한 CacheManager 빈)를 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcertQueryServiceCacheIntegrationTest extends RedisTestContainersConfig {

	@Autowired
	private ConcertQueryService concertQueryService;

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	@Autowired
	private CaffeineCacheStore localCacheStore;

	@MockitoBean
	private ConcertRepository concertRepository;

	@BeforeEach
	void init() {
		localCacheStore.clear(CacheGroup.CONCERT_DETAIL);
		redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
		reset(concertRepository);
	}

	@Test
	@DisplayName("컨텍스트 내 org.springframework.cache.CacheManager 빈은 TieredCacheManager 하나뿐이다")
	void CacheManager_빈은_하나뿐이다() {
		assertThat(applicationContext.getBeansOfType(org.springframework.cache.CacheManager.class)).hasSize(1);
	}

	@Test
	@DisplayName("동일 concertId로 반복 조회해도 Repository는 1번만 호출된다 (Cacheable 배선 검증)")
	void find_반복호출시_리포지토리는_1번만_호출된다() {
		// given
		Long concertId = 1L;
		Venue venue = Venue.builder().id(1L).name("올림픽공원").build();
		Concert concert = Concert.builder().id(concertId).venue(venue).title("콘서트").content("설명").build();
		when(concertRepository.findById(concertId)).thenReturn(Optional.of(concert));

		// when
		FindDTO.Result first = concertQueryService.find(FindDTO.Command.of(concertId));
		FindDTO.Result second = concertQueryService.find(FindDTO.Command.of(concertId));
		FindDTO.Result third = concertQueryService.find(FindDTO.Command.of(concertId));

		// then
		assertThat(first.getConcertId()).isEqualTo(concertId);
		assertThat(second.getConcertId()).isEqualTo(concertId);
		assertThat(third.getConcertId()).isEqualTo(concertId);
		verify(concertRepository, times(1)).findById(concertId);
	}

	@Test
	@DisplayName("존재하지 않는 concertId로 조회하면 캐시를 거치더라도 GeneralException(CONCERT_NOT_FOUND)이 그대로 던져진다")
	void find_존재하지않는_concertId_예외보존() {
		// given
		Long concertId = 999L;
		when(concertRepository.findById(concertId)).thenReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> concertQueryService.find(FindDTO.Command.of(concertId)))
			.isInstanceOf(GeneralException.class)
			.satisfies(e -> assertThat(((GeneralException)e).getCode()).isEqualTo(ConcertErrorCode.CONCERT_NOT_FOUND));
	}
}
