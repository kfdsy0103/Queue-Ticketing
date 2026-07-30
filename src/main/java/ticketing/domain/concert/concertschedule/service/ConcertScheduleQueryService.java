package ticketing.domain.concert.concertschedule.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.concertschedule.dto.FindAllDTO;
import ticketing.domain.concert.concertschedule.dto.FindDTO;
import ticketing.domain.concert.concertschedule.entity.ConcertSchedule;
import ticketing.domain.concert.concertschedule.exception.ConcertScheduleErrorCode;
import ticketing.domain.concert.concertschedule.repository.ConcertScheduleRepository;
import ticketing.domain.concert.scheduleseat.constants.ScheduleSeatRedisKeys;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.concert.scheduleseat.repository.ScheduleSeatGradeProjection;
import ticketing.domain.concert.scheduleseat.repository.ScheduleSeatRepository;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.util.RedisUtil;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertScheduleQueryService {

	private final ConcertScheduleRepository concertScheduleRepository;
	private final ScheduleSeatRepository scheduleSeatRepository;
	private final RedisUtil redisUtil;

	public FindDTO.Result find(FindDTO.Command command) {
		ConcertSchedule concertSchedule = concertScheduleRepository.findById(command.getConcertScheduleId())
			.orElseThrow(() -> new GeneralException(ConcertScheduleErrorCode.CONCERT_SCHEDULE_NOT_FOUND));

		// 엔티티의 일부 컬럼만 있어도 충분하고 도메인 로직 미사용하므로 프로젝션
		List<ScheduleSeatGradeProjection> scheduleSeatGrades =
			scheduleSeatRepository.findSeatGradesByConcertScheduleId(concertSchedule.getId());

		// Redis에 점유 중인 좌석 조회
		Set<Long> occupiedSeatIds = findOccupiedSeatIds(scheduleSeatGrades);

		// 좌석 등급 PK 별로 Mapping
		Map<Long, List<ScheduleSeatGradeProjection>> seatsBySeatGradeId = scheduleSeatGrades.stream()
			.collect(
				Collectors.groupingBy(
					ScheduleSeatGradeProjection::getSeatGradeId,
					LinkedHashMap::new,
					Collectors.toList())
			);

		// 등급별로 지금 당장 예매 가능한 좌석만 카운팅 (DB AVAILABLE + Redis !occupy)
		List<FindDTO.SeatGradeRemaining> seatGradeRemainings = seatsBySeatGradeId.values().stream()
			.map(seats -> FindDTO.SeatGradeRemaining.of(
				seats.getFirst().getSeatGradeId(),
				seats.getFirst().getSeatGradeName(),
				seats.stream()
					.filter(seat ->
						seat.getSeatStatus() == ScheduleSeat.SeatStatus.AVAILABLE
						&& !occupiedSeatIds.contains(seat.getScheduleSeatId())
					).count()
				)
			)
			.toList();

		return FindDTO.Result.from(concertSchedule, seatGradeRemainings);
	}

	public FindAllDTO.Result findAll(FindAllDTO.Command command) {
		return FindAllDTO.Result.builder()
			.concertSchedules(concertScheduleRepository.findAllByConcertId(command.getConcertId()).stream()
				.map(FindAllDTO.Result.ConcertScheduleInfo::from)
				.toList())
			.build();
	}

	/**
	 * 다른 사용자가 선점(occupy) 중인 좌석 ID를 한 번의 MGET으로 조회합니다.
	 */
	private Set<Long> findOccupiedSeatIds(List<ScheduleSeatGradeProjection> scheduleSeatGrades) {

		// DB에는 AVAILABLE, 그러나 점유 여부는 Redis에서 관리하기에 조회
		List<Long> availableSeatIds = scheduleSeatGrades.stream()
			.filter(seat -> seat.getSeatStatus() == ScheduleSeat.SeatStatus.AVAILABLE)
			.map(ScheduleSeatGradeProjection::getScheduleSeatId)
			.toList();

		if (availableSeatIds.isEmpty()) {
			return Set.of();
		}

		List<String> occupyKeys = availableSeatIds.stream()
			.map(ScheduleSeatRedisKeys::occupyKey)
			.toList();
		List<Object> occupyValues = redisUtil.multiGet(occupyKeys);

		return IntStream.range(0, availableSeatIds.size())
			.filter(i -> occupyValues.get(i) != null)
			.mapToObj(availableSeatIds::get)
			.collect(Collectors.toSet());
	}
}
