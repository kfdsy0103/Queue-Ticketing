package ticketing.domain.venue.venue.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.venue.seat.entity.Seat;
import ticketing.domain.venue.seat.repository.SeatRepository;
import ticketing.domain.venue.seatgrade.entity.SeatGrade;
import ticketing.domain.venue.seatgrade.exception.SeatGradeErrorCode;
import ticketing.domain.venue.seatgrade.repository.SeatGradeRepository;
import ticketing.domain.venue.venue.dto.CreateDTO;
import ticketing.domain.venue.venue.entity.Venue;
import ticketing.domain.venue.venue.repository.VenueRepository;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = false)
public class VenueCommandService {

	private final VenueRepository venueRepository;
	private final SeatRepository seatRepository;
	private final SeatGradeRepository seatGradeRepository;

	public CreateDTO.Result create(CreateDTO.Command command) {
		Venue venue = Venue.builder()
			.name(command.getName())
			.build();
		venueRepository.save(venue);

		List<Seat> seats = command.getSeats().stream()
			.map(seatCommand -> {
				SeatGrade seatGrade = seatGradeRepository.findById(seatCommand.getSeatGradeId())
					.orElseThrow(() -> new GeneralException(SeatGradeErrorCode.SEAT_GRADE_NOT_FOUND));

				return Seat.builder()
					.venue(venue)
					.seatGrade(seatGrade)
					.seatNumber(seatCommand.getSeatNumber())
					.build();
			})
			.toList();
		seatRepository.saveAll(seats);

		return CreateDTO.Result.from(venue, seats);
	}
}
