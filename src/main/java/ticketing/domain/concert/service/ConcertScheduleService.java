package ticketing.domain.concert.service;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.dto.ConcertScheduleDTO;
import ticketing.domain.concert.entity.Concert;
import ticketing.domain.concert.entity.ConcertSchedule;
import ticketing.domain.concert.exception.ConcertErrorCode;
import ticketing.domain.concert.repository.ConcertRepository;
import ticketing.domain.concert.repository.ConcertScheduleRepository;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConcertScheduleService {

	private final ConcertScheduleRepository concertScheduleRepository;
	private final ConcertRepository concertRepository;

	public ConcertScheduleDTO.Response create(ConcertScheduleDTO.Command command) {
		ConcertSchedule concertSchedule = ConcertSchedule.builder()
			.concert(findConcert(command.getConcertId()))
			.performanceDate(command.getPerformanceDate())
			.ticketOpenAt(command.getTicketOpenAt())
			.build();

		return ConcertScheduleDTO.Response.from(concertScheduleRepository.save(concertSchedule));
	}

	public ConcertScheduleDTO.Response getById(Long id) {
		return ConcertScheduleDTO.Response.from(findConcertSchedule(id));
	}

	public List<ConcertScheduleDTO.Response> getAll() {
		return concertScheduleRepository.findAll().stream()
			.map(ConcertScheduleDTO.Response::from)
			.toList();
	}

	public ConcertScheduleDTO.Response update(Long id, ConcertScheduleDTO.Command command) {
		ConcertSchedule concertSchedule = findConcertSchedule(id);
		concertSchedule.update(findConcert(command.getConcertId()), command.getPerformanceDate(), command.getTicketOpenAt());

		return ConcertScheduleDTO.Response.from(concertScheduleRepository.save(concertSchedule));
	}

	public void delete(Long id) {
		concertScheduleRepository.delete(findConcertSchedule(id));
	}

	private ConcertSchedule findConcertSchedule(Long id) {
		return concertScheduleRepository.findById(id)
			.orElseThrow(() -> new GeneralException(ConcertErrorCode.CONCERT_SCHEDULE_NOT_FOUND));
	}

	private Concert findConcert(Long id) {
		return concertRepository.findById(id)
			.orElseThrow(() -> new GeneralException(ConcertErrorCode.CONCERT_NOT_FOUND));
	}
}
