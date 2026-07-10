package ticketing.domain.venue.seat.service;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.venue.seat.dto.CreateDTO;
import ticketing.domain.venue.seat.dto.GetAllDTO;
import ticketing.domain.venue.seat.dto.GetByIdDTO;
import ticketing.domain.venue.seat.dto.UpdateDTO;
import ticketing.domain.venue.seat.entity.Seat;
import ticketing.domain.venue.seat.exception.SeatErrorCode;
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
public class SeatService {

	private final SeatRepository seatRepository;
	private final VenueRepository venueRepository;
	private final SeatGradeRepository seatGradeRepository;

	public CreateDTO.Response create(CreateDTO.Command command) {
		Seat seat = Seat.builder()
			.venue(findVenue(command.getVenueId()))
			.seatGrade(findSeatGrade(command.getSeatGradeId()))
			.seatNumber(command.getSeatNumber())
			.build();

		return CreateDTO.Response.from(seatRepository.save(seat));
	}

	public GetByIdDTO.Response getById(Long id) {
		return GetByIdDTO.Response.from(findSeat(id));
	}

	public List<GetAllDTO.Response> getAll() {
		return seatRepository.findAll().stream()
			.map(GetAllDTO.Response::from)
			.toList();
	}

	public UpdateDTO.Response update(Long id, UpdateDTO.Command command) {
		Seat seat = findSeat(id);
		seat.update(findVenue(command.getVenueId()), findSeatGrade(command.getSeatGradeId()), command.getSeatNumber());

		return UpdateDTO.Response.from(seatRepository.save(seat));
	}

	public void delete(Long id) {
		seatRepository.delete(findSeat(id));
	}

	private Seat findSeat(Long id) {
		return seatRepository.findById(id)
			.orElseThrow(() -> new GeneralException(SeatErrorCode.SEAT_NOT_FOUND));
	}

	private Venue findVenue(Long id) {
		return venueRepository.findById(id)
			.orElseThrow(() -> new GeneralException(VenueErrorCode.VENUE_NOT_FOUND));
	}

	private SeatGrade findSeatGrade(Long id) {
		return seatGradeRepository.findById(id)
			.orElseThrow(() -> new GeneralException(SeatGradeErrorCode.SEAT_GRADE_NOT_FOUND));
	}
}
