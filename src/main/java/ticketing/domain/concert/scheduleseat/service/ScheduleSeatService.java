package ticketing.domain.concert.scheduleseat.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.scheduleprice.entity.SchedulePrice;
import ticketing.domain.concert.scheduleprice.exception.SchedulePriceErrorCode;
import ticketing.domain.concert.scheduleprice.repository.SchedulePriceRepository;
import ticketing.domain.concert.scheduleseat.constants.ScheduleSeatRedisKeys;
import ticketing.domain.concert.scheduleseat.dto.FindAllDTO;
import ticketing.domain.concert.scheduleseat.dto.FindOccupyDTO;
import ticketing.domain.concert.scheduleseat.dto.FindRemainingDTO;
import ticketing.domain.concert.scheduleseat.dto.OccupyDTO;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.concert.scheduleseat.enums.SeatDisplayStatus;
import ticketing.domain.concert.scheduleseat.exception.ScheduleSeatErrorCode;
import ticketing.domain.concert.scheduleseat.repository.ScheduleSeatGradeProjection;
import ticketing.domain.concert.scheduleseat.repository.ScheduleSeatRepository;
import ticketing.domain.queue.constants.QueueRedisKeys;
import ticketing.domain.queue.exception.QueueErrorCode;
import ticketing.global.apiPayload.code.GeneralErrorCode;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.config.RedisConfig;
import ticketing.global.constants.CacheName;
import ticketing.global.util.JwtTokenUtil;
import ticketing.global.util.RedisUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleSeatService {

	private static final Duration OCCUPY_TTL = Duration.ofMinutes(5);
	private static final RedisScript<Long> OCCUPY_SCRIPT =
		RedisScript.of(new ClassPathResource("luaScripts/occupy-seats.lua"), Long.class);

	private final ScheduleSeatRepository scheduleSeatRepository;
	private final SchedulePriceRepository schedulePriceRepository;
	private final RedisUtil redisUtil;
	private final JwtTokenUtil jwtTokenUtil;

	/**
	 * 여러 좌석을 5분간 원자적으로 점유(선점)합니다. Lua 스크립트로 전체 좌석을 검사 후 일괄 SET하여,
	 * 다른 사용자가 점유한 좌석이 하나라도 있으면 전체 실패(all-or-nothing) 처리합니다.
	 */
	public OccupyDTO.Result occupy(OccupyDTO.Command command) {

		// 점유하려는 좌석 목록 조회
		List<ScheduleSeat> scheduleSeats = scheduleSeatRepository.findAllById(command.getScheduleSeatIds());
		if (scheduleSeats.size() != command.getScheduleSeatIds().size()) {
			throw new GeneralException(ScheduleSeatErrorCode.SCHEDULE_SEAT_NOT_FOUND);
		}

		// 이미 SOLD 처리된 좌석이 포함되어 있는지 확인
		boolean hasSoldSeat = scheduleSeats.stream()
			.anyMatch(scheduleSeat -> scheduleSeat.getSeatStatus() != ScheduleSeat.SeatStatus.AVAILABLE);
		if (hasSoldSeat) {
			throw new GeneralException(ScheduleSeatErrorCode.NOT_AVAILABLE_SEAT);
		}

		// 토큰에 기록된 userId와 요청자 userId 일치 검사
		Long tokenUserId = jwtTokenUtil.getClaim(command.getToken(), "userId", Long.class);
		if (!command.getUserId().equals(tokenUserId)) {
			throw new GeneralException(GeneralErrorCode.FORBIDDEN);
		}

		// 유저가 해당 회차의 대기열을 통과(Active)했고, 최신 화면(sessionId)의 요청인지 확인
		Long concertScheduleId = scheduleSeats.getFirst().getConcertSchedule().getId();
		String activeKey = QueueRedisKeys.activeKey(concertScheduleId, command.getUserId());
		String storedSessionId = redisUtil.get(activeKey);
		if (storedSessionId == null) {
			throw new GeneralException(QueueErrorCode.NOT_ACTIVE);
		}
		String queueSessionId = jwtTokenUtil.getClaim(command.getToken(), "queueSessionId", String.class);
		if (!queueSessionId.equals(storedSessionId)) {
			throw new GeneralException(QueueErrorCode.SESSION_REVOKED);	// 다른 화면에서 예매를 이어받은 경우 (이 화면은 종료)
		}

		List<String> occupyKeys = command.getScheduleSeatIds().stream()
			.map(ScheduleSeatRedisKeys::occupyKey)
			.toList();

		// 점유 스크립트 실행
		Long occupied = redisUtil.execute(
			OCCUPY_SCRIPT,
			occupyKeys,						// 점유하려는 좌석
			command.getUserId().toString(),	// 점유자 userId
			OCCUPY_TTL.toSeconds()
		);

		// 하나라도 점유되어있는 경우
		if (occupied == null || occupied != 1L) {
			throw new GeneralException(ScheduleSeatErrorCode.ALREADY_OCCUPIED);
		}

		return OccupyDTO.Result.builder()
			.scheduleSeatIds(command.getScheduleSeatIds())
			.expiresAt(LocalDateTime.now().plus(OCCUPY_TTL))
			.build();
	}

	/**
	 * 특정 회차에 대한 좌석 정보를 모두 조회합니다.
	 * 좌석의 상태는 다음 3가지로 분류되고, 이 중 AVAILABLE만 예약 가능합니다.
	 *     - AVAILABLE : 점유되거나 판매되지 않음
	 *     - OCCUPIED : 점유된 상태
	 *     - SOLD : 팔린 상태
	 */
	public FindAllDTO.Result findAll(FindAllDTO.Command command) {

		// 토큰에 기록된 userId와 요청자 userId 일치 검사
		Long tokenUserId = jwtTokenUtil.getClaim(command.getToken(), "userId", Long.class);
		if (!command.getUserId().equals(tokenUserId)) {
			throw new GeneralException(GeneralErrorCode.FORBIDDEN);
		}

		// 유저가 해당 회차의 대기열을 통과(Active)했고, 최신 화면(sessionId)의 요청인지 확인
		String storedSessionId = redisUtil.get(QueueRedisKeys.activeKey(command.getConcertScheduleId(), command.getUserId()));
		if (storedSessionId == null) {
			throw new GeneralException(QueueErrorCode.NOT_ACTIVE);
		}
		String queueSessionId = jwtTokenUtil.getClaim(command.getToken(), "queueSessionId", String.class);
		if (!queueSessionId.equals(storedSessionId)) {
			throw new GeneralException(QueueErrorCode.SESSION_REVOKED);	// 다른 화면에서 예매를 이어받은 경우 (이 화면은 종료)
		}

		List<ScheduleSeat> scheduleSeatEntities = scheduleSeatRepository.findAllByConcertScheduleId(command.getConcertScheduleId());
		if (scheduleSeatEntities.isEmpty()) {
			return FindAllDTO.Result.builder()
				.scheduleSeats(List.of())
				.build();
		}

		// occupy Key 목록을 한 번의 MGET으로 일괄 조회
		List<String> occupyKeys = scheduleSeatEntities.stream()
			.map(scheduleSeat -> ScheduleSeatRedisKeys.occupyKey(scheduleSeat.getId()))
			.toList();
		List<Object> occupyValues = redisUtil.multiGet(occupyKeys);

		List<FindAllDTO.Result.ScheduleSeatInfo> scheduleSeats = IntStream.range(0, scheduleSeatEntities.size())
			.mapToObj(i -> {
				ScheduleSeat scheduleSeat = scheduleSeatEntities.get(i);

				SeatDisplayStatus seatStatus;
				if (scheduleSeat.getSeatStatus() == ScheduleSeat.SeatStatus.SOLD) {
					seatStatus = SeatDisplayStatus.SOLD;
				} else if (occupyValues.get(i) != null) {
					seatStatus = SeatDisplayStatus.OCCUPIED;
				} else {
					seatStatus = SeatDisplayStatus.AVAILABLE;
				}

				return FindAllDTO.Result.ScheduleSeatInfo.of(scheduleSeat, seatStatus);
			})
			.toList();

		return FindAllDTO.Result.builder()
			.scheduleSeats(scheduleSeats)
			.build();
	}

	/**
	 * 좌석 등급별로 지금 당장 예매 가능한 좌석 개수를 조회합니다.
	 * 		- DB AVAILABLE이면서, Redis not Occupied인 좌석을 의미
	 */
	public FindRemainingDTO.Result findRemaining(FindRemainingDTO.Command command) {

		// 필요 컬럼만 프로젝션 조회
		List<ScheduleSeatGradeProjection> scheduleSeatGrades =
			scheduleSeatRepository.findSeatGradesByConcertScheduleIdAndSeatStatus(
				command.getConcertScheduleId(),
				ScheduleSeat.SeatStatus.AVAILABLE
			);

		// Redis에 점유 중인 좌석 조회
		Set<Long> occupiedSeatIds = findOccupiedSeatIds(scheduleSeatGrades);

		// 좌석 등급 별로 Map으로 정리
		Map<Long, List<ScheduleSeatGradeProjection>> seatsPerGrade = scheduleSeatGrades.stream()
			.collect(
				Collectors.groupingBy(
					ScheduleSeatGradeProjection::getSeatGradeId,
					LinkedHashMap::new,
					Collectors.toList())
			);

		// Redis not occupied 필터링
		List<FindRemainingDTO.Result.SeatGradeRemaining> seatGrades = seatsPerGrade.values().stream()
			.map(seats -> FindRemainingDTO.Result.SeatGradeRemaining.of(
				seats.getFirst().getSeatGradeId(),
				seats.getFirst().getSeatGradeName(),
				seats.stream()
					.filter(seat -> !occupiedSeatIds.contains(seat.getScheduleSeatId()))
					.count()
				)
			)
			.toList();

		return FindRemainingDTO.Result.builder()
			.seatGrades(seatGrades)
			.build();
	}

	/**
	 * 자신이 점유 중인 좌석을 검색합니다.
	 */
	public FindOccupyDTO.Result findMyOccupiedSeats(FindOccupyDTO.Command command) {

		// 유저가 해당 회차의 대기열을 통과(Active) 했는지 확인
		String storedSessionId = redisUtil.get(QueueRedisKeys.activeKey(command.getConcertScheduleId(), command.getUserId()));
		if (storedSessionId == null) {
			throw new GeneralException(QueueErrorCode.NOT_ACTIVE);
		}

		List<ScheduleSeat> scheduleSeatEntities = scheduleSeatRepository.findAllByConcertScheduleId(command.getConcertScheduleId());
		if (scheduleSeatEntities.isEmpty()) {
			return FindOccupyDTO.Result.builder().seats(List.of()).build();
		}

		// occupy Key 목록을 한 번의 MGET으로 일괄 조회하여, 본인이 점유 중인 좌석만 골라낸다
		List<String> occupyKeys = scheduleSeatEntities.stream()
			.map(scheduleSeat -> ScheduleSeatRedisKeys.occupyKey(scheduleSeat.getId()))
			.toList();
		List<Object> occupyValues = redisUtil.multiGet(occupyKeys);

		String userIdString = command.getUserId().toString();

		List<FindOccupyDTO.Item> items = IntStream.range(0, scheduleSeatEntities.size())
			.filter(i -> occupyValues.get(i) != null && userIdString.equals(occupyValues.get(i).toString()))
			.mapToObj(i -> {
				ScheduleSeat scheduleSeat = scheduleSeatEntities.get(i);

				SchedulePrice schedulePrice = schedulePriceRepository.findByConcertScheduleIdAndSeatGradeId(
						command.getConcertScheduleId(),
						scheduleSeat.getSeat().getSeatGrade().getId())
					.orElseThrow(() -> new GeneralException(SchedulePriceErrorCode.SCHEDULE_PRICE_NOT_FOUND));

				Long remainingSeconds = redisUtil.getExpire(occupyKeys.get(i));

				return FindOccupyDTO.Item.builder()
					.scheduleSeatId(scheduleSeat.getId())
					.seatId(scheduleSeat.getSeat().getId())
					.seatNumber(scheduleSeat.getSeat().getSeatNumber())
					.seatGradeName(scheduleSeat.getSeat().getSeatGrade().getName())
					.price(schedulePrice.getPrice())
					.remainingSeconds(remainingSeconds)
					.build();
			})
			.toList();

		return FindOccupyDTO.Result.builder()
			.seats(items)
			.build();
	}


	/**
	 * Redis에 선점 상태인 좌석 목록을 한번에 MGET 조회합니다.
	 */
	private Set<Long> findOccupiedSeatIds(List<ScheduleSeatGradeProjection> scheduleSeatGrades) {

		List<Long> availableSeatIds = scheduleSeatGrades.stream()
			.map(ScheduleSeatGradeProjection::getScheduleSeatId)
			.toList();

		if (availableSeatIds.isEmpty()) {
			return Set.of();
		}

		// occupyKey 만들기
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
