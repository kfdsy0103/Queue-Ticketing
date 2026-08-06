package ticketing.domain.concert.concertschedule.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.concertschedule.dto.FindAllDTO;
import ticketing.domain.concert.concertschedule.dto.FindDTO;
import ticketing.domain.concert.concertschedule.entity.ConcertSchedule;
import ticketing.domain.concert.concertschedule.exception.ConcertScheduleErrorCode;
import ticketing.domain.concert.concertschedule.repository.ConcertScheduleRepository;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertScheduleQueryService {

	private final ConcertScheduleRepository concertScheduleRepository;

	public FindDTO.Result find(FindDTO.Command command) {
		ConcertSchedule concertSchedule = concertScheduleRepository.findById(command.getConcertScheduleId())
			.orElseThrow(() -> new GeneralException(ConcertScheduleErrorCode.CONCERT_SCHEDULE_NOT_FOUND));

		return FindDTO.Result.from(concertSchedule);
	}

	public FindAllDTO.Result findAll(FindAllDTO.Command command) {
		return FindAllDTO.Result.builder()
			.concertSchedules(concertScheduleRepository.findAllByConcertId(command.getConcertId()).stream()
				.map(FindAllDTO.Result.ConcertScheduleInfo::from)
				.toList())
			.build();
	}
}
