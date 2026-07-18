package ticketing.domain.seatgrade.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.seatgrade.dto.FindAllDTO;
import ticketing.domain.seatgrade.dto.FindDTO;
import ticketing.domain.seatgrade.entity.SeatGrade;
import ticketing.domain.seatgrade.exception.SeatGradeErrorCode;
import ticketing.domain.seatgrade.repository.SeatGradeRepository;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatGradeQueryService {

	private final SeatGradeRepository seatGradeRepository;

	public FindDTO.Result find(FindDTO.Command command) {
		SeatGrade seatGrade = seatGradeRepository.findById(command.getSeatGradeId())
			.orElseThrow(() -> new GeneralException(SeatGradeErrorCode.SEAT_GRADE_NOT_FOUND));

		return FindDTO.Result.from(seatGrade);
	}

	public FindAllDTO.Result findAll() {
		return FindAllDTO.Result.builder()
			.seatGrades(seatGradeRepository.findAll().stream()
				.map(FindAllDTO.Result.SeatGradeInfo::from)
				.toList())
			.build();
	}
}
