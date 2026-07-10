package ticketing.domain.concert.service;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.dto.SchedulePriceDTO;
import ticketing.domain.concert.entity.ConcertSchedule;
import ticketing.domain.concert.entity.SchedulePrice;
import ticketing.domain.concert.exception.ConcertErrorCode;
import ticketing.domain.concert.repository.ConcertScheduleRepository;
import ticketing.domain.concert.repository.SchedulePriceRepository;
import ticketing.domain.venue.entity.SeatGrade;
import ticketing.domain.venue.exception.VenueErrorCode;
import ticketing.domain.venue.repository.SeatGradeRepository;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulePriceService {

	private final SchedulePriceRepository schedulePriceRepository;
	private final ConcertScheduleRepository concertScheduleRepository;
	private final SeatGradeRepository seatGradeRepository;

	public SchedulePriceDTO.Response create(SchedulePriceDTO.Command command) {
		SchedulePrice schedulePrice = SchedulePrice.builder()
			.concertSchedule(findConcertSchedule(command.getConcertScheduleId()))
			.seatGrade(findSeatGrade(command.getSeatGradeId()))
			.price(command.getPrice())
			.build();

		return SchedulePriceDTO.Response.from(schedulePriceRepository.save(schedulePrice));
	}

	public SchedulePriceDTO.Response getById(Long id) {
		return SchedulePriceDTO.Response.from(findSchedulePrice(id));
	}

	public List<SchedulePriceDTO.Response> getAll() {
		return schedulePriceRepository.findAll().stream()
			.map(SchedulePriceDTO.Response::from)
			.toList();
	}

	public SchedulePriceDTO.Response update(Long id, SchedulePriceDTO.Command command) {
		SchedulePrice schedulePrice = findSchedulePrice(id);
		schedulePrice.update(findConcertSchedule(command.getConcertScheduleId()), findSeatGrade(command.getSeatGradeId()), command.getPrice());

		return SchedulePriceDTO.Response.from(schedulePriceRepository.save(schedulePrice));
	}

	public void delete(Long id) {
		schedulePriceRepository.delete(findSchedulePrice(id));
	}

	private SchedulePrice findSchedulePrice(Long id) {
		return schedulePriceRepository.findById(id)
			.orElseThrow(() -> new GeneralException(ConcertErrorCode.SCHEDULE_PRICE_NOT_FOUND));
	}

	private ConcertSchedule findConcertSchedule(Long id) {
		return concertScheduleRepository.findById(id)
			.orElseThrow(() -> new GeneralException(ConcertErrorCode.CONCERT_SCHEDULE_NOT_FOUND));
	}

	private SeatGrade findSeatGrade(Long id) {
		return seatGradeRepository.findById(id)
			.orElseThrow(() -> new GeneralException(VenueErrorCode.SEAT_GRADE_NOT_FOUND));
	}
}
