package ticketing.domain.concert.scheduleseat.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ticketing.domain.concert.scheduleprice.entity.SchedulePrice;
import ticketing.domain.concert.scheduleprice.exception.SchedulePriceErrorCode;
import ticketing.domain.concert.scheduleprice.repository.SchedulePriceRepository;
import ticketing.domain.concert.scheduleseat.dto.FindMyOccupyDTO;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.concert.scheduleseat.repository.ScheduleSeatRepository;
import ticketing.domain.concert.scheduleseat.projection.ScheduleSeatGradeProjection;
import ticketing.domain.concert.scheduleseat.projection.ScheduleSeatStatusProjection;
import ticketing.global.apiPayload.exception.GeneralException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleSeatQueryService {

	private final ScheduleSeatRepository scheduleSeatRepository;
	private final SchedulePriceRepository schedulePriceRepository;

	/**
	 * 좌석 배치도에 필요한 특정 회차의 좌석 상태 정보를 모두 조회합니다.
	 */
	public List<ScheduleSeatStatusProjection> findSeatStatuses(Long concertScheduleId) {
		return scheduleSeatRepository.findSeatStatusesByConcertScheduleId(concertScheduleId);
	}

	/**
	 * 아직 판매되지 않은, AVAILABLE 상태의 좌석을 등급 정보와 함께 조회합니다.
	 */
	public List<ScheduleSeatGradeProjection> findAvailableSeats(Long concertScheduleId) {
		return scheduleSeatRepository.findAvailableSeatsByConcertScheduleIdAndSeatStatus(
			concertScheduleId,
			ScheduleSeat.SeatStatus.AVAILABLE
		);
	}

	/**
	 * 내가 현재 점유 중인 좌석들의 회차, 등급, 가격을 조회하여 반환합니다.
	 */
	public List<FindMyOccupyDTO.Item> findMyOccupySeats(Map<Long, LocalDateTime> expireTimePerSeat) {

		// 회차, 등급까지 fetch join으로 함께 조회
		List<ScheduleSeat> scheduleSeats =
			scheduleSeatRepository.findAllByIdInWithScheduleAndSeatGrade(expireTimePerSeat.keySet());

		// 회차, 등급 쌍으로 가격 Mapping
		Set<Long> concertScheduleIds = scheduleSeats.stream()
			.map(scheduleSeat -> scheduleSeat.getConcertSchedule().getId())
			.collect(Collectors.toSet());

		Map<FindMyOccupyDTO.SchedulePriceKey, Integer> priceByScheduleAndGrade =
			schedulePriceRepository.findAllByConcertScheduleIdIn(concertScheduleIds).stream()
				.collect(Collectors.toMap(
					schedulePrice -> FindMyOccupyDTO.SchedulePriceKey.of(
						schedulePrice.getConcertSchedule().getId(),
						schedulePrice.getSeatGrade().getId()
					),
					SchedulePrice::getPrice
				));

		// 결과 DTO 생성
		return scheduleSeats.stream()
			.map(scheduleSeat -> {
				Integer price = priceByScheduleAndGrade.get(FindMyOccupyDTO.SchedulePriceKey.of(
					scheduleSeat.getConcertSchedule().getId(),
					scheduleSeat.getSeat().getSeatGrade().getId()
				));
				if (price == null) {
					throw new GeneralException(SchedulePriceErrorCode.SCHEDULE_PRICE_NOT_FOUND);
				}

				return FindMyOccupyDTO.Item.of(scheduleSeat, price, expireTimePerSeat.get(scheduleSeat.getId()));
			})
			.toList();
	}
}
