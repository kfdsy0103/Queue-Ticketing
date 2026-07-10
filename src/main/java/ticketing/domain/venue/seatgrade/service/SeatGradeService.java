package ticketing.domain.venue.seatgrade.service;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.venue.seatgrade.dto.CreateDTO;
import ticketing.domain.venue.seatgrade.dto.GetAllDTO;
import ticketing.domain.venue.seatgrade.dto.GetByIdDTO;
import ticketing.domain.venue.seatgrade.dto.UpdateDTO;
import ticketing.domain.venue.seatgrade.entity.SeatGrade;
import ticketing.domain.venue.seatgrade.exception.SeatGradeErrorCode;
import ticketing.domain.venue.seatgrade.repository.SeatGradeRepository;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatGradeService {

	private final SeatGradeRepository seatGradeRepository;

	public CreateDTO.Response create(CreateDTO.Command command) {
		SeatGrade seatGrade = SeatGrade.builder()
			.name(command.getName())
			.build();

		return CreateDTO.Response.from(seatGradeRepository.save(seatGrade));
	}

	public GetByIdDTO.Response getById(Long id) {
		return GetByIdDTO.Response.from(findSeatGrade(id));
	}

	public List<GetAllDTO.Response> getAll() {
		return seatGradeRepository.findAll().stream()
			.map(GetAllDTO.Response::from)
			.toList();
	}

	public UpdateDTO.Response update(Long id, UpdateDTO.Command command) {
		SeatGrade seatGrade = findSeatGrade(id);
		seatGrade.update(command.getName());

		return UpdateDTO.Response.from(seatGradeRepository.save(seatGrade));
	}

	public void delete(Long id) {
		seatGradeRepository.delete(findSeatGrade(id));
	}

	private SeatGrade findSeatGrade(Long id) {
		return seatGradeRepository.findById(id)
			.orElseThrow(() -> new GeneralException(SeatGradeErrorCode.SEAT_GRADE_NOT_FOUND));
	}
}
