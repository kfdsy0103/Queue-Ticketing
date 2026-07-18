package ticketing.domain.queue.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
	private static final RedisScript<Long> PROMOTE_SCRIPT =
		RedisScript.of(new ClassPathResource("luaScripts/promote-queue.lua"), Long.class);

	private final RedisUtil redisUtil;
	private final ConcertScheduleRepository concertScheduleRepository;

	/**
	 * 티켓이 오픈됐고 공연일이 지나지 않은 콘서트 회차 목록을 조회하여,
	 * 회차별로 1초마다 100명씩 입장 처리(Active)합니다.
	 * ShedLock으로 다중 인스턴스에서의 스케쥴러 동작 제어 추가
	*/
	@Async("schedulerTaskExecutor")
	@Scheduled(fixedDelay = 1000)
	@SchedulerLock(name = "queuePromotionScheduler", lockAtLeastFor = "PT1S", lockAtMostFor = "PT30S")
	public void promoteScheduler() {
		LocalDateTime now = LocalDateTime.now();
		log.debug("[QueuePromotionScheduler] promote() 스케쥴러 동작, now: {}", now);

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
	 * concertSchedulerId에 해당하는 대기열의 앞 쪽에서 PROMOTION_BATCH_SIZE 만큼을 작업열로 이동시킵니다.
	 * 이때, ZPOPMIN + 작업열 등록은 Lua로 처리합니다.
	 * 	이는 Enter API에서 발생하는 상태 체크 시 Race Condition을 방지하기 위함입니다.
	 */
	private void promote(Long concertScheduleId) {

		String waitingKey = QueueRedisKeys.waitingKey(concertScheduleId);
		String activeKey = QueueRedisKeys.activeKey(concertScheduleId);
		String userInfoKey = QueueRedisKeys.userInfoKey(concertScheduleId);

		// '대기열에서 빼고 -> 작업열로 활성화(sessionId 복사)' 를 원자 처리
		Long promotedCount = redisUtil.execute(
			PROMOTE_SCRIPT,
			List.of(waitingKey, activeKey, userInfoKey),
			PROMOTION_BATCH_SIZE,
			ACTIVE_TTL.toSeconds()
		);

		if (promotedCount > 0) {
			log.info("[QueuePromotionScheduler] concertScheduleId={} 사용자 {}명 입장 처리", concertScheduleId, promotedCount);
		}
	}
}
