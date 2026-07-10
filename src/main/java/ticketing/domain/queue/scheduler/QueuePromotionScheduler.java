package ticketing.domain.queue.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.concertschedule.entity.ConcertSchedule;
import ticketing.domain.concert.concertschedule.repository.ConcertScheduleRepository;
import ticketing.domain.queue.constants.QueueRedisKeys;
import ticketing.global.util.RedisUtil;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueuePromotionScheduler {

	private static final long PROMOTION_BATCH_SIZE = 100;
	private static final Duration ACTIVE_TTL = Duration.ofMinutes(7);

	private final RedisUtil redisUtil;
	private final ConcertScheduleRepository concertScheduleRepository;

	/**
	 * 티켓이 오픈됐고 공연일이 지나지 않은 콘서트 회차 목록을 조회하여,
	 * 회차별로 2초마다 100명씩 입장 처리(Active)합니다.
	*/
	@Async("queueTaskExecutor")
	@Scheduled(fixedDelay = 2000)
	public void promoteScheduler() {
		LocalDateTime now = LocalDateTime.now();
		log.info("[QueuePromotionScheduler] promote() 스케쥴러 동작, now: {}", now);

		// 유효한 콘서트 회차 조회
		List<ConcertSchedule> openSchedules = concertScheduleRepository
			.findAllByTicketOpenAtBeforeAndPerformanceDateGreaterThanEqual(now, now.toLocalDate());

		for (ConcertSchedule schedule : openSchedules) {
			try {
				promote(schedule.getId());
			} catch (Exception e) {
				log.error("[QueuePromotionScheduler] concertScheduleId={} 처리 중 오류", schedule.getId(), e);
			}
		}
	}

	/**
	 * 콘서트 회차 하나에 대해 대기열 앞쪽 사용자 100명을 입장 처리합니다.
	 * 입장 처리된 사용자는 Hash 내의 개별 필드 TTL(HEXPIRE)로 관리됩니다. Redis 7.4+
	 */
	private void promote(Long concertScheduleId) {

		// 해당되는 콘서트 회차의 대기열 조회
		String waitingKey = QueueRedisKeys.waitingKey(concertScheduleId);
		String activeKey = QueueRedisKeys.activeKey(concertScheduleId);

		// ZPOPMIN 앞에서 100개
		Set<ZSetOperations.TypedTuple<Object>> promoted = redisUtil.zPopMin(waitingKey, PROMOTION_BATCH_SIZE);
		if (promoted == null || promoted.isEmpty()) {
			return;
		}

		for (ZSetOperations.TypedTuple<Object> tuple : promoted) {
			String userId = tuple.getValue().toString();
			redisUtil.hSet(activeKey, userId, "1");
			redisUtil.hExpire(activeKey, ACTIVE_TTL, userId);
		}

		log.info("[QueuePromotionScheduler] concertScheduleId={} 사용자 {}명 입장 처리", concertScheduleId, promoted.size());
	}
}
