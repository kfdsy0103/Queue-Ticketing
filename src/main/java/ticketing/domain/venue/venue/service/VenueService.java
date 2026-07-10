package ticketing.domain.venue.venue.service;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.venue.venue.dto.CreateDTO;
import ticketing.domain.venue.venue.dto.GetAllDTO;
import ticketing.domain.venue.venue.dto.GetByIdDTO;
import ticketing.domain.venue.venue.dto.UpdateDTO;
import ticketing.domain.venue.venue.entity.Venue;
import ticketing.domain.venue.venue.exception.VenueErrorCode;
import ticketing.domain.venue.venue.repository.VenueRepository;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
public class VenueService {

	private final VenueRepository venueRepository;

	public CreateDTO.Response create(CreateDTO.Command command) {
		Venue venue = Venue.builder()
			.name(command.getName())
			.build();

		return CreateDTO.Response.from(venueRepository.save(venue));
	}

	public GetByIdDTO.Response getById(Long id) {
		return GetByIdDTO.Response.from(findVenue(id));
	}

	public List<GetAllDTO.Response> getAll() {
		return venueRepository.findAll().stream()
			.map(GetAllDTO.Response::from)
			.toList();
	}

	public UpdateDTO.Response update(Long id, UpdateDTO.Command command) {
		Venue venue = findVenue(id);
		venue.update(command.getName());

		return UpdateDTO.Response.from(venueRepository.save(venue));
	}

	public void delete(Long id) {
		venueRepository.delete(findVenue(id));
	}

	private Venue findVenue(Long id) {
		return venueRepository.findById(id)
			.orElseThrow(() -> new GeneralException(VenueErrorCode.VENUE_NOT_FOUND));
	}
}
