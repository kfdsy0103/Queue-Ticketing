package ticketing.domain.concert.concert.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.concert.dto.CreateDTO;
import ticketing.domain.concert.concert.entity.Concert;
import ticketing.domain.concert.concert.repository.ConcertRepository;
import ticketing.domain.concert.concertschedule.entity.ConcertSchedule;
import ticketing.domain.concert.concertschedule.repository.ConcertScheduleRepository;
import ticketing.domain.concert.scheduleprice.entity.SchedulePrice;
import ticketing.domain.concert.scheduleprice.repository.SchedulePriceRepository;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.concert.scheduleseat.repository.ScheduleSeatRepository;
import ticketing.domain.seatgrade.exception.SeatGradeErrorCode;
import ticketing.domain.seatgrade.repository.SeatGradeRepository;
import ticketing.domain.venue.seat.entity.Seat;
import ticketing.domain.venue.seat.repository.SeatRepository;
import ticketing.domain.venue.venue.entity.Venue;
import ticketing.domain.venue.venue.exception.VenueErrorCode;
import ticketing.domain.venue.venue.repository.VenueRepository;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = false)
public class ConcertCommandService {

	private final ConcertRepository concertRepository;
	private final ConcertScheduleRepository concertScheduleRepository;
	private final SchedulePriceRepository schedulePriceRepository;
	private final ScheduleSeatRepository scheduleSeatRepository;
	private final VenueRepository venueRepository;
	private final SeatGradeRepository seatGradeRepository;
	private final SeatRepository seatRepository;

	public CreateDTO.Result create(CreateDTO.Command command) {

		// 공연장 검색
		Venue venue = venueRepository.findById(command.getVenueId())
			.orElseThrow(() -> new GeneralException(VenueErrorCode.VENUE_NOT_FOUND));

		// 콘서트 생성
		Concert concert = Concert.builder()
			.venue(venue)
			.title(command.getTitle())
			.content(command.getContent())
			.build();
		concertRepository.save(concert);

		// 공연장의 모든 좌석 조회 (모든 회차에서 공통으로 사용)
		List<Seat> venueSeats = seatRepository.findAllByVenueId(venue.getId());

		List<ConcertSchedule> concertSchedules = new ArrayList<>();

		// 입력 인자로 받은 스케쥴 일정에 따라 콘서트 회차 생성
		for (CreateDTO.Command.ScheduleCommand scheduleCommand : command.getSchedules()) {

			// 회차 생성
			ConcertSchedule concertSchedule = ConcertSchedule.builder()
				.concert(concert)
				.performanceDate(scheduleCommand.getPerformanceDate())
				.ticketOpenAt(scheduleCommand.getTicketOpenAt())
				.build();
			concertScheduleRepository.save(concertSchedule);
			concertSchedules.add(concertSchedule);

			// 입력받은 값으로 좌석 등급별 가격 책정
			List<SchedulePrice> schedulePrices = scheduleCommand.getPrices().stream()
				.map(priceCommand ->
					SchedulePrice.builder()
					.concertSchedule(concertSchedule)
					.seatGrade(seatGradeRepository.findById(priceCommand.getSeatGradeId())
						.orElseThrow(() -> new GeneralException(SeatGradeErrorCode.SEAT_GRADE_NOT_FOUND))
					)
					.price(priceCommand.getPrice())
					.build()
				)
				.toList();
			schedulePriceRepository.saveAll(schedulePrices);

			// 공연장의 모든 좌석을 해당 회차의 AVAILABLE 좌석으로 등록
			List<ScheduleSeat> scheduleSeats = venueSeats.stream()
				.map(seat ->
					ScheduleSeat.builder()
					.concertSchedule(concertSchedule)
					.seat(seat)
					.seatStatus(ScheduleSeat.SeatStatus.AVAILABLE)
					.build()
				)
				.toList();
			scheduleSeatRepository.saveAll(scheduleSeats);
		}

		// 응답 Result DTO 반환
		return CreateDTO.Result.of(concert, concertSchedules);
	}
}
