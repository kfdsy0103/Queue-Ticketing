package ticketing.domain.concert.service;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.dto.ConcertDTO;
import ticketing.domain.concert.entity.Concert;
import ticketing.domain.concert.exception.ConcertErrorCode;
import ticketing.domain.concert.repository.ConcertRepository;
import ticketing.domain.venue.entity.Venue;
import ticketing.domain.venue.exception.VenueErrorCode;
import ticketing.domain.venue.repository.VenueRepository;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConcertService {

	private final ConcertRepository concertRepository;
	private final VenueRepository venueRepository;

	public ConcertDTO.Response create(ConcertDTO.Command command) {
		Concert concert = Concert.builder()
			.venue(findVenue(command.getVenueId()))
			.title(command.getTitle())
			.content(command.getContent())
			.build();

		return ConcertDTO.Response.from(concertRepository.save(concert));
	}

	public ConcertDTO.Response getById(Long id) {
		return ConcertDTO.Response.from(findConcert(id));
	}

	public List<ConcertDTO.Response> getAll() {
		return concertRepository.findAll().stream()
			.map(ConcertDTO.Response::from)
			.toList();
	}

	public ConcertDTO.Response update(Long id, ConcertDTO.Command command) {
		Concert concert = findConcert(id);
		concert.update(findVenue(command.getVenueId()), command.getTitle(), command.getContent());

		return ConcertDTO.Response.from(concertRepository.save(concert));
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
