package ticketing.domain.queue.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import ticketing.domain.concert.concertschedule.entity.ConcertSchedule;
import ticketing.domain.concert.concertschedule.repository.ConcertScheduleRepository;
import ticketing.domain.queue.service.QueueService;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true")
public class QueuePromotionScheduler {

	private final ConcertScheduleRepository concertScheduleRepository;
	private final QueueService queueService;

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
				Long count = queueService.promote(schedule.getId());
				if (count > 0) {
					log.info("[QueuePromotionScheduler] concertScheduleId={} 사용자 {}명 입장 처리", schedule.getId(), count);
				}
			} catch (Exception e) {
				log.error("[QueuePromotionScheduler] concertScheduleId={} 처리 중 오류", schedule.getId(), e);
			}
		}
	}
}
