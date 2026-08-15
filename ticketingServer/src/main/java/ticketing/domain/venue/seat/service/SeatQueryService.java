package ticketing.domain.venue.seat.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.venue.seat.dto.FindAllDTO;
import ticketing.domain.venue.seat.repository.SeatRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatQueryService {

	private final SeatRepository seatRepository;

	public FindAllDTO.Result findAll(FindAllDTO.Command command) {
		return FindAllDTO.Result.builder()
			.seatInfos(seatRepository.findAllByVenueId(command.getVenueId()).stream()
				.map(FindAllDTO.Result.SeatInfo::from)
				.toList())
			.build();
	}
}
