package ticketing.domain.concert.concert.service;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.concert.dto.CreateDTO;
import ticketing.domain.concert.concert.dto.GetAllDTO;
import ticketing.domain.concert.concert.dto.GetByIdDTO;
import ticketing.domain.concert.concert.dto.UpdateDTO;
import ticketing.domain.concert.concert.entity.Concert;
import ticketing.domain.concert.concert.exception.ConcertErrorCode;
import ticketing.domain.concert.concert.repository.ConcertRepository;
import ticketing.domain.venue.venue.entity.Venue;
import ticketing.domain.venue.venue.exception.VenueErrorCode;
import ticketing.domain.venue.venue.repository.VenueRepository;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConcertService {

	private final ConcertRepository concertRepository;
	private final VenueRepository venueRepository;

	public CreateDTO.Response create(CreateDTO.Command command) {
		Concert concert = Concert.builder()
			.venue(findVenue(command.getVenueId()))
			.title(command.getTitle())
			.content(command.getContent())
			.build();

		return CreateDTO.Response.from(concertRepository.save(concert));
	}

	public GetByIdDTO.Response getById(Long id) {
		return GetByIdDTO.Response.from(findConcert(id));
	}

	public List<GetAllDTO.Response> getAll() {
		return concertRepository.findAll().stream()
			.map(GetAllDTO.Response::from)
			.toList();
	}

	public UpdateDTO.Response update(Long id, UpdateDTO.Command command) {
		Concert concert = findConcert(id);
		concert.update(findVenue(command.getVenueId()), command.getTitle(), command.getContent());

		return UpdateDTO.Response.from(concertRepository.save(concert));
	}

	public void delete(Long id) {
		concertRepository.delete(findConcert(id));
	}

	private Concert findConcert(Long id) {
		return concertRepository.findById(id)
			.orElseThrow(() -> new GeneralException(ConcertErrorCode.CONCERT_NOT_FOUND));
	}

	private Venue findVenue(Long id) {
		return venueRepository.findById(id)
			.orElseThrow(() -> new GeneralException(VenueErrorCode.VENUE_NOT_FOUND));
	}
}
