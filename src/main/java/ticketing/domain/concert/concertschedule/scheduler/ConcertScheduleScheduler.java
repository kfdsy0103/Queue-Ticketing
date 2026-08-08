package ticketing.domain.concert.concertschedule.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import ticketing.domain.concert.concert.service.ConcertQueryService;
import ticketing.domain.concert.concertschedule.entity.ConcertSchedule;
import ticketing.domain.concert.concertschedule.repository.ConcertScheduleRepository;
import ticketing.domain.concert.concertschedule.service.ConcertScheduleQueryService;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConcertScheduleScheduler {

	// 워밍업을 시작할 시간대 설정, 10분 전부터 워밍업하도록 설정
	private static final Duration WARMUP_BEFORE_OPEN = Duration.ofMinutes(10);
	// 10분 전부터 30초 마다 워밍업 조회 시도
	private static final long WARMUP_INTERVAL_MS = 30000L;

	private final ConcertScheduleRepository concertScheduleRepository;
	private final ConcertScheduleQueryService concertScheduleQueryService;
	private final ConcertQueryService concertQueryService;

	/**
	 * 스템피드 방지를 위해 티켓 오픈이 10분 안으로 다가온 회차의 캐시를 미리 갱신해둡니다.
	 * 오픈 이후에는 조회가 활발할 것으로 예상되므로 -> PER에서 자연스럽게 갱신됨
	 */
	@Async("schedulerTaskExecutor")
	@Scheduled(fixedDelay = WARMUP_INTERVAL_MS)
	@SchedulerLock(name = "concertCacheWarmUpScheduler", lockAtLeastFor = "PT10S", lockAtMostFor = "PT1M")
	public void warmUpConcertScheduleCaches() {

		LocalDateTime now = LocalDateTime.now();
		log.debug("[ConcertScheduleScheduler] warmUpConcertScheduleCaches() 스케쥴러 동작, now: {}", now);

		// 티켓 오픈까지 10분 남은 회차들을 조회
		List<ConcertSchedule> targetSchedules = concertScheduleRepository.findAllByTicketOpenAtBetween(now, now.plus(WARMUP_BEFORE_OPEN));

		Set<Long> concertIds = new LinkedHashSet<>();

		// 콘서트 회차 정보 워밍업
		for (ConcertSchedule schedule : targetSchedules) {
			concertIds.add(schedule.getConcert().getId());
			try {
				concertScheduleQueryService.warmUpCache(schedule.getId());
			} catch (Exception e) {
				log.error("[ConcertScheduleScheduler] concertScheduleId={} 캐시 워밍업 중 오류", schedule.getId(), e);
			}
		}

		// 콘서트 정보 워밍업
		for (Long concertId : concertIds) {
			try {
				concertQueryService.warmUpCache(concertId);
			} catch (Exception e) {
				log.error("[ConcertScheduleScheduler] concertId={} 캐시 워밍업 중 오류", concertId, e);
			}
		}

		if (!targetSchedules.isEmpty()) {
			log.info(
				"[ConcertScheduleScheduler] 캐시 워밍업 완료. 회차 {}건, 콘서트 {}건",
				targetSchedules.size(),
				concertIds.size()
			);
		}
	}
}
