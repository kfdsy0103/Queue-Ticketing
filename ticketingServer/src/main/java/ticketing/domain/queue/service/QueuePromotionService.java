package ticketing.domain.queue.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import ticketing.domain.queue.constants.QueueRedisKeys;
import ticketing.global.util.RedisUtil;

/**
 * 대기열에서 작업열(Active)로 승격, Ticketing 서버가 감당할 수 있는만큼 유입량 처리
 */
@Slf4j
@Service
public class QueuePromotionService {

	private static final RedisScript<Long> PROMOTE_SCRIPT =
		RedisScript.of(new ClassPathResource("luaScripts/promote-queue.lua"), Long.class);

	private final RedisUtil redisUtil;

	/**
	 * 한 번의 승격에서 작업열로 넘길 인원 수.
	 * 실제 초당 유입량은 이 값과 스케쥴러 주기(ticketing.scheduler.queue.promotion-cron, 기본 1초)의
	 * 조합으로 정해지므로 둘 중 하나만 바꾸면 의도한 유입량이 나오지 않는다.
	 * 부하 테스트로 측정한 티켓팅 API TPS를 넘지 않도록 잡는다.
	 */
	private final long promotionBatchSize;

	/**
	 * 작업열(Active) 슬롯의 유지 시간(초). 기본 420초 = 7분.
	 * 대기열 서버의 이어받기(takeover)도 같은 activeKey 에 TTL 을 다시 걸므로,
	 * 두 서버가 같은 값(queue.active-ttl-seconds)을 보도록 맞춰야 한다.
	 */
	private final long activeTtlSeconds;

	public QueuePromotionService(
		RedisUtil redisUtil,
		@Value("${ticketing.scheduler.queue.promotion-batch-size:15}") long promotionBatchSize,
		@Value("${queue.active-ttl-seconds:420}") long activeTtlSeconds
	) {
		this.redisUtil = redisUtil;
		this.promotionBatchSize = promotionBatchSize;
		this.activeTtlSeconds = activeTtlSeconds;
	}

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
			promotionBatchSize,
			activeTtlSeconds
		);
	}
}
