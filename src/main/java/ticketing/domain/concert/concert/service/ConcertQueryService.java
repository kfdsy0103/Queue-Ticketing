package ticketing.domain.concert.concert.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.concert.dto.FindAllDTO;
import ticketing.domain.concert.concert.dto.FindDTO;
import ticketing.domain.concert.concert.exception.ConcertErrorCode;
import ticketing.domain.concert.concert.repository.ConcertRepository;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.cache.enums.CacheGroup;
import ticketing.global.util.CacheService;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertQueryService {

	private final ConcertRepository concertRepository;
	private final CacheService cacheService;

	/**
	 * PER 예방 및 분산락 기반 스템피드 대응 캐시 적용
	 * 		cache:concertDetail:{concertId}
	 */
	public FindDTO.Result find(FindDTO.Command command) {
		return cacheService.getCacheWithPER(
			CacheGroup.CONCERT_DETAIL,
			command.getConcertId().toString(),
			() -> concertRepository.findById(command.getConcertId())
				.map(FindDTO.Result::from)
				.orElseThrow(() -> new GeneralException(ConcertErrorCode.CONCERT_NOT_FOUND))
		);
	}

	public FindAllDTO.Result findAll() {
		return FindAllDTO.Result.of(concertRepository.findAll().stream()
			.map(FindAllDTO.ConcertInfo::from)
			.toList());
	}

	/**
	 * 티켓 오픈 10분 직전부터 스케쥴러에서 호출되는 워밍업 메서드.
	 */
	public void warmUpCache(Long concertId) {
		cacheService.getCacheWithPER(
			CacheGroup.CONCERT_DETAIL,
			concertId.toString(),
			() -> concertRepository.findById(concertId)
				.map(FindDTO.Result::from)
				.orElseThrow(() -> new GeneralException(ConcertErrorCode.CONCERT_NOT_FOUND))
		);
	}
}
