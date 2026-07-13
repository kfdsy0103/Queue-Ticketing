package ticketing.domain.venue.seat.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.venue.seat.dto.CreateDTO;
import ticketing.domain.venue.seat.entity.Seat;
import ticketing.domain.venue.seat.repository.SeatRepository;
import ticketing.domain.venue.seatgrade.entity.SeatGrade;
import ticketing.domain.venue.seatgrade.exception.SeatGradeErrorCode;
import ticketing.domain.venue.seatgrade.repository.SeatGradeRepository;
import ticketing.domain.venue.venue.entity.Venue;
import ticketing.domain.venue.venue.exception.VenueErrorCode;
import ticketing.domain.venue.venue.repository.VenueRepository;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = false)
public class SeatCommandService {

	private final SeatRepository seatRepository;
	private final VenueRepository venueRepository;
	private final SeatGradeRepository seatGradeRepository;

	public CreateDTO.Result create(CreateDTO.Command command) {
		Venue venue = venueRepository.findById(command.getVenueId())
			.orElseThrow(() -> new GeneralException(VenueErrorCode.VENUE_NOT_FOUND));

		SeatGrade seatGrade = seatGradeRepository.findById(command.getSeatGradeId())
			.orElseThrow(() -> new GeneralException(SeatGradeErrorCode.SEAT_GRADE_NOT_FOUND));

		Seat seat = Seat.builder()
			.venue(venue)
			.seatGrade(seatGrade)
			.seatNumber(command.getSeatNumber())
			.build();

		return CreateDTO.Result.from(seatRepository.save(seat));
	}
}
