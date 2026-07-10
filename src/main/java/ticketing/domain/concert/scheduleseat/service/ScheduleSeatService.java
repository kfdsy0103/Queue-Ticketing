package ticketing.domain.concert.scheduleseat.service;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.concertschedule.entity.ConcertSchedule;
import ticketing.domain.concert.concertschedule.exception.ConcertScheduleErrorCode;
import ticketing.domain.concert.concertschedule.repository.ConcertScheduleRepository;
import ticketing.domain.concert.scheduleseat.dto.CreateDTO;
import ticketing.domain.concert.scheduleseat.dto.GetAllDTO;
import ticketing.domain.concert.scheduleseat.dto.GetByIdDTO;
import ticketing.domain.concert.scheduleseat.dto.UpdateDTO;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.concert.scheduleseat.exception.ScheduleSeatErrorCode;
import ticketing.domain.concert.scheduleseat.repository.ScheduleSeatRepository;
import ticketing.domain.venue.seat.entity.Seat;
import ticketing.domain.venue.seat.exception.SeatErrorCode;
import ticketing.domain.venue.seat.repository.SeatRepository;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleSeatService {

	private final ScheduleSeatRepository scheduleSeatRepository;
	private final ConcertScheduleRepository concertScheduleRepository;
	private final SeatRepository seatRepository;

	public CreateDTO.Response create(CreateDTO.Command command) {
		ScheduleSeat scheduleSeat = ScheduleSeat.builder()
			.concertSchedule(findConcertSchedule(command.getConcertScheduleId()))
			.seat(findSeat(command.getSeatId()))
			.seatStatus(command.getSeatStatus())
			.build();

		return CreateDTO.Response.from(scheduleSeatRepository.save(scheduleSeat));
	}

	public GetByIdDTO.Response getById(Long id) {
		return GetByIdDTO.Response.from(findScheduleSeat(id));
	}

	public List<GetAllDTO.Response> getAll() {
		return scheduleSeatRepository.findAll().stream()
			.map(GetAllDTO.Response::from)
			.toList();
	}

	public UpdateDTO.Response update(Long id, UpdateDTO.Command command) {
		ScheduleSeat scheduleSeat = findScheduleSeat(id);
		scheduleSeat.update(findConcertSchedule(command.getConcertScheduleId()), findSeat(command.getSeatId()), command.getSeatStatus());

		return UpdateDTO.Response.from(scheduleSeatRepository.save(scheduleSeat));
	}

	public void delete(Long id) {
		scheduleSeatRepository.delete(findScheduleSeat(id));
	}

	private ScheduleSeat findScheduleSeat(Long id) {
		return scheduleSeatRepository.findById(id)
			.orElseThrow(() -> new GeneralException(ScheduleSeatErrorCode.SCHEDULE_SEAT_NOT_FOUND));
	}

	private ConcertSchedule findConcertSchedule(Long id) {
		return concertScheduleRepository.findById(id)
			.orElseThrow(() -> new GeneralException(ConcertScheduleErrorCode.CONCERT_SCHEDULE_NOT_FOUND));
	}

	private Seat findSeat(Long id) {
		return seatRepository.findById(id)
			.orElseThrow(() -> new GeneralException(SeatErrorCode.SEAT_NOT_FOUND));
	}
}
