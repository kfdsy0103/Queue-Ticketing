package ticketing.domain.venue.venue.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.venue.venue.dto.FindAllDTO;
import ticketing.domain.venue.venue.dto.FindDTO;
import ticketing.domain.venue.venue.entity.Venue;
import ticketing.domain.venue.venue.exception.VenueErrorCode;
import ticketing.domain.venue.venue.repository.VenueRepository;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VenueQueryService {

	private final VenueRepository venueRepository;

	public FindDTO.Result find(FindDTO.Command command) {
		Venue venue = venueRepository.findById(command.getVenueId())
			.orElseThrow(() -> new GeneralException(VenueErrorCode.VENUE_NOT_FOUND));

		return FindDTO.Result.from(venue);
	}

	public FindAllDTO.Result findAll() {
		return FindAllDTO.Result.builder()
			.venues(venueRepository.findAll().stream()
				.map(FindAllDTO.Result.VenueInfo::from)
				.toList())
			.build();
	}
}
