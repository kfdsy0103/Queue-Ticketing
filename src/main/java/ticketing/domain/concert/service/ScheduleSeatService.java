package ticketing.domain.concert.service;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.dto.ScheduleSeatDTO;
import ticketing.domain.concert.entity.ConcertSchedule;
import ticketing.domain.concert.entity.ScheduleSeat;
import ticketing.domain.concert.exception.ConcertErrorCode;
import ticketing.domain.concert.repository.ConcertScheduleRepository;
import ticketing.domain.concert.repository.ScheduleSeatRepository;
import ticketing.domain.venue.entity.Seat;
import ticketing.domain.venue.exception.VenueErrorCode;
import ticketing.domain.venue.repository.SeatRepository;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleSeatService {

	private final ScheduleSeatRepository scheduleSeatRepository;
	private final ConcertScheduleRepository concertScheduleRepository;
	private final SeatRepository seatRepository;

	public ScheduleSeatDTO.Response create(ScheduleSeatDTO.Command command) {
		ScheduleSeat scheduleSeat = ScheduleSeat.builder()
			.concertSchedule(findConcertSchedule(command.getConcertScheduleId()))
			.seat(findSeat(command.getSeatId()))
			.seatStatus(command.getSeatStatus())
			.build();

		return ScheduleSeatDTO.Response.from(scheduleSeatRepository.save(scheduleSeat));
	}

	public ScheduleSeatDTO.Response getById(Long id) {
		return ScheduleSeatDTO.Response.from(findScheduleSeat(id));
	}

	public List<ScheduleSeatDTO.Response> getAll() {
		return scheduleSeatRepository.findAll().stream()
			.map(ScheduleSeatDTO.Response::from)
			.toList();
	}

	public ScheduleSeatDTO.Response update(Long id, ScheduleSeatDTO.Command command) {
		ScheduleSeat scheduleSeat = findScheduleSeat(id);
		scheduleSeat.update(findConcertSchedule(command.getConcertScheduleId()), findSeat(command.getSeatId()), command.getSeatStatus());

		return ScheduleSeatDTO.Response.from(scheduleSeatRepository.save(scheduleSeat));
	}

	public void delete(Long id) {
		scheduleSeatRepository.delete(findScheduleSeat(id));
	}

	private ScheduleSeat findScheduleSeat(Long id) {
		return scheduleSeatRepository.findById(id)
			.orElseThrow(() -> new GeneralException(ConcertErrorCode.SCHEDULE_SEAT_NOT_FOUND));
	}

	private ConcertSchedule findConcertSchedule(Long id) {
		return concertScheduleRepository.findById(id)
			.orElseThrow(() -> new GeneralException(ConcertErrorCode.CONCERT_SCHEDULE_NOT_FOUND));
	}

	private Seat findSeat(Long id) {
		return seatRepository.findById(id)
			.orElseThrow(() -> new GeneralException(VenueErrorCode.SEAT_NOT_FOUND));
	}
}
