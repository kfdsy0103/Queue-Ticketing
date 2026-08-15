package ticketing.domain.queue.service;

import java.time.Duration;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.queue.constants.QueueRedisKeys;
import ticketing.global.util.RedisUtil;

/**
 * 대기열에서 작업열(Active)로 승격, Ticketing 서버가 감당할 수 있는만큼 유입량 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueuePromotionService {

	private static final Duration ACTIVE_TTL = Duration.ofMinutes(7);
	private static final long PROMOTION_BATCH_SIZE = 8;
	private static final RedisScript<Long> PROMOTE_SCRIPT =
		RedisScript.of(new ClassPathResource("luaScripts/promote-queue.lua"), Long.class);

	private final RedisUtil redisUtil;

	/**
	 * 'ZPOPMIN + ACTIVE 등록'은 Lua로 하여,
	 * popmin과 active 등록 사이 status 조회가 실행되면 에러로 출력되는 race confition 방지
	 */
	public Long promote(Long concertScheduleId) {

		String waitingKey = QueueRedisKeys.waitingKey(concertScheduleId);
		String activeKeyPrefix = QueueRedisKeys.activeKeyPrefix(concertScheduleId);
		String userInfoKeyPrefix = QueueRedisKeys.userInfoKeyPrefix(concertScheduleId);

		return redisUtil.execute(
			PROMOTE_SCRIPT,
			List.of(waitingKey, activeKeyPrefix, userInfoKeyPrefix),
			PROMOTION_BATCH_SIZE,
			ACTIVE_TTL.toSeconds()
		);
	}
}
