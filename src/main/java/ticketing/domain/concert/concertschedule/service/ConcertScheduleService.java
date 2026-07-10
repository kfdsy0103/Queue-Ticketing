package ticketing.domain.concert.concertschedule.service;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.concert.entity.Concert;
import ticketing.domain.concert.concert.exception.ConcertErrorCode;
import ticketing.domain.concert.concert.repository.ConcertRepository;
import ticketing.domain.concert.concertschedule.dto.CreateDTO;
import ticketing.domain.concert.concertschedule.dto.GetAllDTO;
import ticketing.domain.concert.concertschedule.dto.GetByIdDTO;
import ticketing.domain.concert.concertschedule.dto.UpdateDTO;
import ticketing.domain.concert.concertschedule.entity.ConcertSchedule;
import ticketing.domain.concert.concertschedule.exception.ConcertScheduleErrorCode;
import ticketing.domain.concert.concertschedule.repository.ConcertScheduleRepository;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConcertScheduleService {

	private final ConcertScheduleRepository concertScheduleRepository;
	private final ConcertRepository concertRepository;

	public CreateDTO.Response create(CreateDTO.Command command) {
		ConcertSchedule concertSchedule = ConcertSchedule.builder()
			.concert(findConcert(command.getConcertId()))
			.performanceDate(command.getPerformanceDate())
			.ticketOpenAt(command.getTicketOpenAt())
			.build();

		return CreateDTO.Response.from(concertScheduleRepository.save(concertSchedule));
	}

	public GetByIdDTO.Response getById(Long id) {
		return GetByIdDTO.Response.from(findConcertSchedule(id));
	}

	public List<GetAllDTO.Response> getAll() {
		return concertScheduleRepository.findAll().stream()
			.map(GetAllDTO.Response::from)
			.toList();
	}

	public UpdateDTO.Response update(Long id, UpdateDTO.Command command) {
		ConcertSchedule concertSchedule = findConcertSchedule(id);
		concertSchedule.update(findConcert(command.getConcertId()), command.getPerformanceDate(), command.getTicketOpenAt());

		return UpdateDTO.Response.from(concertScheduleRepository.save(concertSchedule));
	}

	public void delete(Long id) {
		concertScheduleRepository.delete(findConcertSchedule(id));
	}

	private ConcertSchedule findConcertSchedule(Long id) {
		return concertScheduleRepository.findById(id)
			.orElseThrow(() -> new GeneralException(ConcertScheduleErrorCode.CONCERT_SCHEDULE_NOT_FOUND));
	}

	private Concert findConcert(Long id) {
		return concertRepository.findById(id)
			.orElseThrow(() -> new GeneralException(ConcertErrorCode.CONCERT_NOT_FOUND));
	}
}
