package ticketing.domain.concert.scheduleseat.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.concert.scheduleseat.exception.ScheduleSeatErrorCode;
import ticketing.domain.concert.scheduleseat.repository.ScheduleSeatRepository;
import ticketing.global.apiPayload.exception.GeneralException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = false)
public class ScheduleSeatCommandService {

	private final ScheduleSeatRepository scheduleSeatRepository;

	// Master DB에서 검증 (@transcation reaonly false)
	public void validateOccupy(Long concertScheduleId, List<Long> scheduleSeatIds) {

		// 점유하려는 좌석 목록 조회
		List<ScheduleSeat> scheduleSeats = scheduleSeatRepository.findAllByIdInAndConcertScheduleId(scheduleSeatIds, concertScheduleId);
		if (scheduleSeats.size() != scheduleSeatIds.size()) {
			throw new GeneralException(ScheduleSeatErrorCode.SCHEDULE_SEAT_NOT_FOUND);
		}

		// 이미 SOLD 처리된 좌석이 포함되어 있는지 확인
		boolean hasSoldSeat = scheduleSeats.stream()
			.anyMatch(scheduleSeat -> scheduleSeat.getSeatStatus() != ScheduleSeat.SeatStatus.AVAILABLE);
		if (hasSoldSeat) {
			throw new GeneralException(ScheduleSeatErrorCode.NOT_AVAILABLE_SEAT);
		}
	}
}
