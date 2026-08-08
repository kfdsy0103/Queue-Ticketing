package ticketing.domain.concert.concertschedule.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.concertschedule.dto.FindAllDTO;
import ticketing.domain.concert.concertschedule.dto.FindDTO;
import ticketing.domain.concert.concertschedule.exception.ConcertScheduleErrorCode;
import ticketing.domain.concert.concertschedule.repository.ConcertScheduleRepository;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.enums.CacheGroup;
import ticketing.global.util.CacheService;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertScheduleQueryService {

	private final ConcertScheduleRepository concertScheduleRepository;
	private final CacheService cacheService;

	/**
	 * PER 예방 및 분산락 기반 스템피드 대응 캐시 적용
	 * 		cache:concertScheduleDetail:{concertScheduleId}
	 */
	public FindDTO.Result find(FindDTO.Command command) {
		return cacheService.getCacheWithPER(
			CacheGroup.CONCERT_SCHEDULE_DETAIL,
			command.getConcertScheduleId().toString(),
			() -> concertScheduleRepository.findById(command.getConcertScheduleId())
				.map(FindDTO.Result::from)
				.orElseThrow(() -> new GeneralException(ConcertScheduleErrorCode.CONCERT_SCHEDULE_NOT_FOUND))
		);
	}

	public FindAllDTO.Result findAll(FindAllDTO.Command command) {
		return FindAllDTO.Result.of(concertScheduleRepository.findAllByConcertId(command.getConcertId()).stream()
			.map(FindAllDTO.ConcertScheduleInfo::from)
			.toList());
	}

	/**
	 * 티켓 오픈 10분 직전부터 스케쥴러에서 호출되는 워밍업 메서드.
	 */
	public void warmUpCache(Long concertScheduleId) {
		cacheService.getCacheWithPER(
			CacheGroup.CONCERT_SCHEDULE_DETAIL,
			concertScheduleId.toString(),
			() -> concertScheduleRepository.findById(concertScheduleId)
				.map(FindDTO.Result::from)
				.orElseThrow(() -> new GeneralException(ConcertScheduleErrorCode.CONCERT_SCHEDULE_NOT_FOUND))
		);
	}
}
