package ticketing.domain.queue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ticketing.domain.queue.constants.QueueRedisKeys;
import ticketing.global.util.RedisUtil;

/**
 * QueuePromotionService가 Lua 스크립트에 넘기는 KEYS 순서와 배치 크기·TTL을 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class QueuePromotionServiceTest {

	private static final long PROMOTION_BATCH_SIZE = 15L;
	private static final long ACTIVE_TTL_SECONDS = 420L;

	@InjectMocks
	private QueuePromotionService queuePromotionService;

	@Mock
	private RedisUtil redisUtil;

	@Test
	void 대기열과_active와_userInfo_키를_순서대로_넘겨_승격_스크립트를_실행한다() {
		// given
		given(redisUtil.<Long>execute(any(), any(), any(), any())).willReturn(3L);

		// when
		Long promoted = queuePromotionService.promote(1L);

		// then
		assertThat(promoted).isEqualTo(3L);
		verify(redisUtil).execute(
			any(),
			eq(List.of(
				QueueRedisKeys.waitingKey(1L),
				QueueRedisKeys.activeKeyPrefix(1L),
				QueueRedisKeys.userInfoKeyPrefix(1L)
			)),
			eq(PROMOTION_BATCH_SIZE),
			eq(ACTIVE_TTL_SECONDS)
		);
	}
}
