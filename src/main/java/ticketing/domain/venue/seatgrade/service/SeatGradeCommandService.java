package ticketing.domain.venue.seatgrade.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.venue.seatgrade.dto.CreateDTO;
import ticketing.domain.venue.seatgrade.entity.SeatGrade;
import ticketing.domain.venue.seatgrade.repository.SeatGradeRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = false)
public class SeatGradeCommandService {

	private final SeatGradeRepository seatGradeRepository;

	public CreateDTO.Result create(CreateDTO.Command command) {
		SeatGrade seatGrade = SeatGrade.builder()
			.name(command.getName())
			.build();

		return CreateDTO.Result.from(seatGradeRepository.save(seatGrade));
	}
}
