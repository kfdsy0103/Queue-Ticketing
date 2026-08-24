package ticketing.domain.concert.concert.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.concert.dto.FindAllDTO;
import ticketing.domain.concert.concert.dto.FindDTO;
import ticketing.domain.concert.concert.exception.ConcertErrorCode;
import ticketing.domain.concert.concert.repository.ConcertRepository;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.cache.constants.CacheName;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertQueryService {

	private final ConcertRepository concertRepository;

	/**
	 * PER 예방 및 분산락 기반 스템피드 대응 캐시 적용 (TieredCache)
	 * 		cache:concertDetail:{concertId}
	 */
	@Cacheable(cacheNames = CacheName.CONCERT_DETAIL, key = "#command.concertId", sync = true)
	public FindDTO.Result find(FindDTO.Command command) {
		return concertRepository.findById(command.getConcertId())
			.map(FindDTO.Result::from)
			.orElseThrow(() -> new GeneralException(ConcertErrorCode.CONCERT_NOT_FOUND));
	}

	public FindAllDTO.Result findAll() {
		return FindAllDTO.Result.of(concertRepository.findAll().stream()
			.map(FindAllDTO.ConcertInfo::from)
			.toList());
	}

	/**
	 * 티켓 오픈 10분 직전부터 스케쥴러에서 호출되는 워밍업 메서드.
	 */
	@Cacheable(cacheNames = CacheName.CONCERT_DETAIL, key = "#concertId", sync = true)
	public FindDTO.Result warmUpCache(Long concertId) {
		return concertRepository.findById(concertId)
			.map(FindDTO.Result::from)
			.orElseThrow(() -> new GeneralException(ConcertErrorCode.CONCERT_NOT_FOUND));
	}
}
