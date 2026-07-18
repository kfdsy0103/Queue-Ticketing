package ticketing.domain.venue.venue.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.seatgrade.entity.SeatGrade;
import ticketing.domain.seatgrade.exception.SeatGradeErrorCode;
import ticketing.domain.seatgrade.repository.SeatGradeRepository;
import ticketing.domain.venue.seat.entity.Seat;
import ticketing.domain.venue.seat.repository.SeatRepository;
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

		// 콘서트장 생성
		Venue venue = Venue.builder()
			.name(command.getName())
			.build();
		venueRepository.save(venue);

		// 좌석 등급 저장
		List<SeatGrade> seatGrades = command.getSeatGrades().stream()
			.map(seatGradeCommand -> SeatGrade.builder()
				.name(seatGradeCommand.getName())
				.build())
			.toList();
		seatGradeRepository.saveAll(seatGrades);

		// 좌석 이름 - 좌석 등급 Map 생성 (다음 코드에서 좌석별 등급 매핑을 위함)
		Map<String, SeatGrade> seatGradesByName = seatGrades.stream()
			.collect(Collectors.toMap(SeatGrade::getName, Function.identity()));

		// 등급을 매핑하여 좌석 저장
		List<Seat> seats = command.getSeats().stream()
			.map(seatCommand -> {
				SeatGrade seatGrade = seatGradesByName.get(seatCommand.getSeatGradeName());
				if (seatGrade == null) {
					throw new GeneralException(SeatGradeErrorCode.SEAT_GRADE_NOT_FOUND);
				}
				return Seat.builder()
					.venue(venue)
					.seatGrade(seatGrade)
					.seatNumber(seatCommand.getSeatNumber())
					.build();
			})
			.toList();
		seatRepository.saveAll(seats);

		return CreateDTO.Result.from(venue, seatGrades, seats);
	}
}
