package ticketing.domain.concert.scheduleseat.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.scheduleprice.entity.SchedulePrice;
import ticketing.domain.concert.scheduleprice.exception.SchedulePriceErrorCode;
import ticketing.domain.concert.scheduleprice.repository.SchedulePriceRepository;
import ticketing.domain.concert.scheduleseat.constants.ScheduleSeatRedisKeys;
import ticketing.domain.concert.scheduleseat.dto.FindAllDTO;
import ticketing.domain.concert.scheduleseat.dto.FindMyOccupyDTO;
import ticketing.domain.concert.scheduleseat.dto.FindRemainingDTO;
import ticketing.domain.concert.scheduleseat.dto.OccupyDTO;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.concert.scheduleseat.enums.SeatDisplayStatus;
import ticketing.domain.concert.scheduleseat.exception.ScheduleSeatErrorCode;
import ticketing.domain.concert.scheduleseat.repository.projection.ScheduleSeatGradeProjection;
import ticketing.domain.concert.scheduleseat.repository.ScheduleSeatRepository;
import ticketing.domain.concert.scheduleseat.repository.projection.ScheduleSeatStatusProjection;
import ticketing.domain.queue.constants.QueueRedisKeys;
import ticketing.domain.queue.exception.QueueErrorCode;
import ticketing.global.apiPayload.code.GeneralErrorCode;
import ticketing.global.apiPayload.exception.GeneralException;
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

		// 콘서트 회차 PK
		Long concertScheduleId = scheduleSeats.getFirst().getConcertSchedule().getId();

		// 유저가 해당 회차의 대기열을 통과(Active)했는지 확인
		String storedSessionId = redisUtil.get(QueueRedisKeys.activeKey(concertScheduleId, command.getUserId()));
		if (storedSessionId == null) {
			throw new GeneralException(QueueErrorCode.NOT_ACTIVE);
		}

		// 동일한 sessionId의 요청인지 확인
		String queueSessionId = jwtTokenUtil.getClaim(command.getToken(), "queueSessionId", String.class);
		if (!queueSessionId.equals(storedSessionId)) {
			throw new GeneralException(QueueErrorCode.SESSION_REVOKED);	// 다른 화면에서 예매를 이어받은 경우 (이 화면은 종료)
		}

		// 좌석 점유 Key
		List<String> occupyKeys = command.getScheduleSeatIds().stream()
			.map(ScheduleSeatRedisKeys::occupyKey)
			.toList();

		// 조회용 인덱스 Key (사용자별 / 회차별)
		String userOccupyKey = ScheduleSeatRedisKeys.userOccupyKey(command.getUserId());
		String scheduleOccupyKey = ScheduleSeatRedisKeys.scheduleOccupyKey(concertScheduleId);

		// KEY 생성
		List<String> keys = Stream.concat(
			occupyKeys.stream(),
			Stream.of(userOccupyKey, scheduleOccupyKey)
		).toList();

		// ARGV 생성
		long now = System.currentTimeMillis();
		long expiresAtMillis = now + OCCUPY_TTL.toMillis();
		List<Object> args = new ArrayList<>();
		args.add(command.getUserId());
		args.add(OCCUPY_TTL.toSeconds());
		args.add(expiresAtMillis);
		args.addAll(command.getScheduleSeatIds());

		// 점유 스크립트 실행
		Long occupied = redisUtil.execute(OCCUPY_SCRIPT, keys, args.toArray());

		// 하나라도 점유되어있는 경우
		if (occupied == null || occupied != 1L) {
			throw new GeneralException(ScheduleSeatErrorCode.ALREADY_OCCUPIED);
		}

		return OccupyDTO.Result.of(command.getScheduleSeatIds(), toLocalDateTime(expiresAtMillis));
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

		// 콘서트 회차 PK
		Long concertScheduleId = command.getConcertScheduleId();

		// 유저가 해당 회차의 대기열을 통과(Active)했는지 확인
		String storedSessionId = redisUtil.get(QueueRedisKeys.activeKey(concertScheduleId, command.getUserId()));
		if (storedSessionId == null) {
			throw new GeneralException(QueueErrorCode.NOT_ACTIVE);
		}

		// 동일한 sessionId의 요청인지 확인
		String queueSessionId = jwtTokenUtil.getClaim(command.getToken(), "queueSessionId", String.class);
		if (!queueSessionId.equals(storedSessionId)) {
			throw new GeneralException(QueueErrorCode.SESSION_REVOKED);	// 다른 화면에서 예매를 이어받은 경우 (이 화면은 종료)
		}

		// 필요 컬럼만 프로젝션 조회
		List<ScheduleSeatStatusProjection> scheduleSeatStatuses = scheduleSeatRepository.findSeatStatusesByConcertScheduleId(concertScheduleId);
		if (scheduleSeatStatuses.isEmpty()) {
			return FindAllDTO.Result.empty();
		}

		// 회차 인덱스에서 조회
		Set<Long> occupiedSeatIds = findOccupiedSeatIdsInSchedule(concertScheduleId);

		List<FindAllDTO.ScheduleSeatInfo> scheduleSeats = scheduleSeatStatuses.stream()
			.map(scheduleSeat -> {
				SeatDisplayStatus seatStatus;
				if (scheduleSeat.getSeatStatus() == ScheduleSeat.SeatStatus.SOLD) {
					seatStatus = SeatDisplayStatus.SOLD;
				} else if (occupiedSeatIds.contains(scheduleSeat.getScheduleSeatId())) {
					seatStatus = SeatDisplayStatus.OCCUPIED;
				} else {
					seatStatus = SeatDisplayStatus.AVAILABLE;
				}

				return FindAllDTO.ScheduleSeatInfo.of(
					scheduleSeat,
					concertScheduleId,
					seatStatus
				);
			})
			.toList();

		return FindAllDTO.Result.of(scheduleSeats);
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

		// 회차 인덱스에서 조회
		Set<Long> occupiedSeatIds = findOccupiedSeatIdsInSchedule(command.getConcertScheduleId());

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

		return FindRemainingDTO.Result.of(seatGrades);
	}

	/**
	 * 자신이 현재 점유 중인 좌석을 검색합니다.
	 * 	- Redis에서 사용자별 조회용 인덱스를 기반으로 조회하여 남은 시간도 함께 반환.
	 * 	- ZSET의 Score를 만료 시간으로 설정하여, 남은 시간을 바로 확인하도록
	 */
	public FindMyOccupyDTO.Result findMyOccupy(FindMyOccupyDTO.Command command) {

		// 조회용 유저 인덱스 키 획득
		String userOccupyKey = ScheduleSeatRedisKeys.userOccupyKey(command.getUserId());

		// 검색
		Set<ZSetOperations.TypedTuple<Object>> occupiedTuples = redisUtil.zRangeWithScores(userOccupyKey);
		if (occupiedTuples == null || occupiedTuples.isEmpty()) {
			return FindMyOccupyDTO.Result.empty();
		}

		long now = System.currentTimeMillis();
		Map<Long, LocalDateTime> expireTimePerSeat = new LinkedHashMap<>();

		// 만료 시간이 지난건 필터링
		for (ZSetOperations.TypedTuple<Object> tuple : occupiedTuples) {
			if (tuple.getValue() == null || tuple.getScore() == null || tuple.getScore() <= now) {
				continue;
			}
			expireTimePerSeat.put(
				Long.valueOf(tuple.getValue().toString()),
				toLocalDateTime(tuple.getScore().longValue())
			);
		}

		if (expireTimePerSeat.isEmpty()) {
			return FindMyOccupyDTO.Result.empty();
		}

		// 좌석 조회
		List<ScheduleSeat> scheduleSeats =
			scheduleSeatRepository.findAllByIdInWithScheduleAndSeatGrade(expireTimePerSeat.keySet());

		// 회차, 등급 쌍으로 가격 Mapping
		Set<Long> concertScheduleIds = scheduleSeats.stream()
			.map(scheduleSeat -> scheduleSeat.getConcertSchedule().getId())
			.collect(Collectors.toSet());

		Map<FindMyOccupyDTO.SchedulePriceKey, Integer> priceByScheduleAndGrade = schedulePriceRepository.findAllByConcertScheduleIdIn(concertScheduleIds).stream()
			.collect(Collectors.toMap(
				schedulePrice -> FindMyOccupyDTO.SchedulePriceKey.of(
					schedulePrice.getConcertSchedule().getId(),
					schedulePrice.getSeatGrade().getId()
				),
				SchedulePrice::getPrice
			));

		// 결과 DTO 생성
		List<FindMyOccupyDTO.Item> items = scheduleSeats.stream()
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

		return FindMyOccupyDTO.Result.of(items);
	}

	/**
	 * 회차 인덱스 ZSET에서 현재 점유 중인 좌석 ID 목록을 조회합니다.
	 */
	private Set<Long> findOccupiedSeatIdsInSchedule(Long concertScheduleId) {
		Set<Object> members = redisUtil.zRangeByScoreFrom(
			ScheduleSeatRedisKeys.scheduleOccupyKey(concertScheduleId),
			System.currentTimeMillis()	// from, 아직 만료되지 않은 것을 조회함
		);
		if (members == null || members.isEmpty()) {
			return Set.of();
		}

		return members.stream()
			.map(member -> Long.valueOf(member.toString()))
			.collect(Collectors.toSet());
	}

	/**
	 * epoch millis를 LocalDateTime으로 변환하는 메서드
	 */
	private LocalDateTime toLocalDateTime(long epochMillis) {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
	}
}
