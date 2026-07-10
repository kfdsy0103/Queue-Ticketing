package ticketing.domain.concert.scheduleprice.service;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.concertschedule.entity.ConcertSchedule;
import ticketing.domain.concert.concertschedule.exception.ConcertScheduleErrorCode;
import ticketing.domain.concert.concertschedule.repository.ConcertScheduleRepository;
import ticketing.domain.concert.scheduleprice.dto.CreateDTO;
import ticketing.domain.concert.scheduleprice.dto.GetAllDTO;
import ticketing.domain.concert.scheduleprice.dto.GetByIdDTO;
import ticketing.domain.concert.scheduleprice.dto.UpdateDTO;
import ticketing.domain.concert.scheduleprice.entity.SchedulePrice;
import ticketing.domain.concert.scheduleprice.exception.SchedulePriceErrorCode;
import ticketing.domain.concert.scheduleprice.repository.SchedulePriceRepository;
import ticketing.domain.venue.seatgrade.entity.SeatGrade;
import ticketing.domain.venue.seatgrade.exception.SeatGradeErrorCode;
import ticketing.domain.venue.seatgrade.repository.SeatGradeRepository;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulePriceService {

	private final SchedulePriceRepository schedulePriceRepository;
	private final ConcertScheduleRepository concertScheduleRepository;
	private final SeatGradeRepository seatGradeRepository;

	public CreateDTO.Response create(CreateDTO.Command command) {
		SchedulePrice schedulePrice = SchedulePrice.builder()
			.concertSchedule(findConcertSchedule(command.getConcertScheduleId()))
			.seatGrade(findSeatGrade(command.getSeatGradeId()))
			.price(command.getPrice())
			.build();

		return CreateDTO.Response.from(schedulePriceRepository.save(schedulePrice));
	}

	public GetByIdDTO.Response getById(Long id) {
		return GetByIdDTO.Response.from(findSchedulePrice(id));
	}

	public List<GetAllDTO.Response> getAll() {
		return schedulePriceRepository.findAll().stream()
			.map(GetAllDTO.Response::from)
			.toList();
	}

	public UpdateDTO.Response update(Long id, UpdateDTO.Command command) {
		SchedulePrice schedulePrice = findSchedulePrice(id);
		schedulePrice.update(findConcertSchedule(command.getConcertScheduleId()), findSeatGrade(command.getSeatGradeId()), command.getPrice());

		return UpdateDTO.Response.from(schedulePriceRepository.save(schedulePrice));
	}

	public void delete(Long id) {
		schedulePriceRepository.delete(findSchedulePrice(id));
	}

	private SchedulePrice findSchedulePrice(Long id) {
		return schedulePriceRepository.findById(id)
			.orElseThrow(() -> new GeneralException(SchedulePriceErrorCode.SCHEDULE_PRICE_NOT_FOUND));
	}

	private ConcertSchedule findConcertSchedule(Long id) {
		return concertScheduleRepository.findById(id)
			.orElseThrow(() -> new GeneralException(ConcertScheduleErrorCode.CONCERT_SCHEDULE_NOT_FOUND));
	}

	private SeatGrade findSeatGrade(Long id) {
		return seatGradeRepository.findById(id)
			.orElseThrow(() -> new GeneralException(SeatGradeErrorCode.SEAT_GRADE_NOT_FOUND));
	}
}
