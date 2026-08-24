package ticketing.domain.concert.concertschedule.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.concertschedule.dto.FindAllDTO;
import ticketing.domain.concert.concertschedule.dto.FindDTO;
import ticketing.domain.concert.concertschedule.exception.ConcertScheduleErrorCode;
import ticketing.domain.concert.concertschedule.repository.ConcertScheduleRepository;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.cache.constants.CacheName;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertScheduleQueryService {

	private final ConcertScheduleRepository concertScheduleRepository;

	/**
	 * PER 예방 및 분산락 기반 스템피드 대응 캐시 적용 (TieredCache)
	 * 		cache:concertScheduleDetail:{concertScheduleId}
	 */
	@Cacheable(cacheNames = CacheName.CONCERT_SCHEDULE_DETAIL, key = "#command.concertScheduleId", sync = true)
	public FindDTO.Result find(FindDTO.Command command) {
		return concertScheduleRepository.findById(command.getConcertScheduleId())
			.map(FindDTO.Result::from)
			.orElseThrow(() -> new GeneralException(ConcertScheduleErrorCode.CONCERT_SCHEDULE_NOT_FOUND));
	}

	public FindAllDTO.Result findAll(FindAllDTO.Command command) {
		return FindAllDTO.Result.of(concertScheduleRepository.findAllByConcertId(command.getConcertId()).stream()
			.map(FindAllDTO.ConcertScheduleInfo::from)
			.toList());
	}

	/**
	 * 티켓 오픈 10분 직전부터 스케쥴러에서 호출되는 워밍업 메서드.
	 */
	@Cacheable(cacheNames = CacheName.CONCERT_SCHEDULE_DETAIL, key = "#concertScheduleId", sync = true)
	public FindDTO.Result warmUpCache(Long concertScheduleId) {
		return concertScheduleRepository.findById(concertScheduleId)
			.map(FindDTO.Result::from)
			.orElseThrow(() -> new GeneralException(ConcertScheduleErrorCode.CONCERT_SCHEDULE_NOT_FOUND));
	}
}
