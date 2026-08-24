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
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.scheduleseat.constants.ScheduleSeatRedisKeys;
import ticketing.domain.concert.scheduleseat.dto.FindAllDTO;
import ticketing.domain.concert.scheduleseat.dto.FindMyOccupyDTO;
import ticketing.domain.concert.scheduleseat.dto.FindRemainingDTO;
import ticketing.domain.concert.scheduleseat.dto.OccupyDTO;
import ticketing.domain.concert.scheduleseat.dto.ScheduleSeatLayoutDTO;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.concert.scheduleseat.enums.SeatDisplayStatus;
import ticketing.domain.concert.scheduleseat.exception.ScheduleSeatErrorCode;
import ticketing.domain.concert.scheduleseat.projection.ScheduleSeatGradeProjection;
import ticketing.domain.queue.constants.QueueRedisKeys;
import ticketing.domain.queue.exception.QueueErrorCode;
import ticketing.global.apiPayload.code.GeneralErrorCode;
import ticketing.global.apiPayload.exception.GeneralException;
import ticketing.global.util.JwtTokenUtil;
import ticketing.global.util.RedisUtil;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleSeatFacadeService {

	private static final Duration OCCUPY_TTL = Duration.ofMinutes(5);	// 좌석 점유 유지 TTL

	private static final RedisScript<Long> OCCUPY_SCRIPT =
		RedisScript.of(new ClassPathResource("luaScripts/occupy-seats.lua"), Long.class);

	private final ScheduleSeatQueryService scheduleSeatQueryService;
	private final ScheduleSeatCommandService scheduleSeatCommandService;
	private final RedisUtil redisUtil;
	private final JwtTokenUtil jwtTokenUtil;

	/**
	 * 여러 좌석을 5분간 원자적으로 점유(선점)합니다. Lua 스크립트로 전체 좌석을 검사 후 일괄 SET하여,
	 * 다른 사용자가 점유한 좌석이 하나라도 있으면 전체 실패(all-or-nothing) 처리합니다.
	 */
	public OccupyDTO.Result occupy(OccupyDTO.Command command) {

		// 토큰 및 Active 검증
		validateTokenOwner(command.getUserId(), command.getToken());
		validateActiveQueue(command.getConcertScheduleId(), command.getUserId(), command.getToken());

		// 좌석 SOLD 여부 검증 (Master DB에서 검증, lag면 이미 팔린 좌석 점유 문제)
		scheduleSeatCommandService.validateOccupy(command.getConcertScheduleId(), command.getScheduleSeatIds());

		// 좌석 점유 Key
		List<String> occupyKeys = command.getScheduleSeatIds().stream()
			.map(ScheduleSeatRedisKeys::occupyKey)
			.toList();

		// 조회용 인덱스 Key (사용자별 / 회차별)
		String userOccupyKey = ScheduleSeatRedisKeys.userOccupyKey(command.getUserId());
		String scheduleOccupyKey = ScheduleSeatRedisKeys.scheduleOccupyKey(command.getConcertScheduleId());

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

		return OccupyDTO.Result.of(
			command.getScheduleSeatIds(),
			toLocalDateTime(now + OCCUPY_TTL.toMillis())
		);
	}

	/**
	 * 특정 회차에 대한 좌석 정보를 모두 조회합니다.
	 * 좌석의 상태는 다음 3가지로 분류되고, 이 중 AVAILABLE만 예약 가능합니다.
	 *     - AVAILABLE : 점유되거나 판매되지 않음
	 *     - OCCUPIED : 점유된 상태
	 *     - SOLD : 팔린 상태
	 */
	public FindAllDTO.Result findAll(FindAllDTO.Command command) {

		// Token 및 Active 검증
		validateTokenOwner(command.getUserId(), command.getToken());
		validateActiveQueue(command.getConcertScheduleId(), command.getUserId(), command.getToken());

		// 좌석 배치(정적 정보)는 캐시에서 조회
		List<ScheduleSeatLayoutDTO.Item> scheduleSeatStatuses = scheduleSeatQueryService.findSeatStatuses(command.getConcertScheduleId());
		if (scheduleSeatStatuses.isEmpty()) {
			return FindAllDTO.Result.empty();
		}

		// 회차 인덱스에서 조회
		Set<Long> occupiedSeatIds = findOccupiedSeatIdsInSchedule(command.getConcertScheduleId());

		List<FindAllDTO.ScheduleSeatInfo> scheduleSeats = scheduleSeatStatuses.stream()
			.map(scheduleSeat -> {
				SeatDisplayStatus seatStatus;
				if (scheduleSeat.getSeatStatus() == ScheduleSeat.SeatStatus.SOLD) {
					seatStatus = SeatDisplayStatus.SOLD;		// 팔린 상태
				} else if (occupiedSeatIds.contains(scheduleSeat.getScheduleSeatId())) {
					seatStatus = SeatDisplayStatus.OCCUPIED;	// 이미 점유된 상태
				} else {
					seatStatus = SeatDisplayStatus.AVAILABLE;	// 점유 가능한 상태
				}

				return FindAllDTO.ScheduleSeatInfo.of(
					scheduleSeat,
					command.getConcertScheduleId(),
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
		List<ScheduleSeatGradeProjection> scheduleSeatGrades = scheduleSeatQueryService.findAvailableSeats(command.getConcertScheduleId());

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

		for (ZSetOperations.TypedTuple<Object> tuple : occupiedTuples) {
			if (tuple.getValue() == null || tuple.getScore() == null) {
				continue;
			}

			long expiresAtMillis = tuple.getScore().longValue();
			if (expiresAtMillis <= now) {
				continue;
			}

			expireTimePerSeat.put(
				Long.valueOf(tuple.getValue().toString()),	// id
				toLocalDateTime(expiresAtMillis)			// 사용자에게 보여줄 남은 시간
			);
		}

		if (expireTimePerSeat.isEmpty()) {
			return FindMyOccupyDTO.Result.empty();
		}

		// 좌석 조회 (회차, 등급, 가격까지 채워서)
		List<FindMyOccupyDTO.Item> items = scheduleSeatQueryService.findMyOccupySeats(expireTimePerSeat);

		return FindMyOccupyDTO.Result.of(items);
	}

	/**
	 * 토큰에 기록된 userId와 요청자 userId가 같은지 검증
	 */
	private void validateTokenOwner(Long userId, String token) {
		Long tokenUserId = jwtTokenUtil.getClaim(token, "userId", Long.class);
		if (!userId.equals(tokenUserId)) {
			throw new GeneralException(GeneralErrorCode.FORBIDDEN);
		}
	}

	/**
	 * 유저가 해당 회차의 대기열을 통과(Active)했고, 최신 화면(sessionId)의 요청인지 검증
	 */
	private void validateActiveQueue(Long concertScheduleId, Long userId, String token) {
		String storedSessionId = redisUtil.get(QueueRedisKeys.activeKey(concertScheduleId, userId));
		if (storedSessionId == null) {
			throw new GeneralException(QueueErrorCode.NOT_ACTIVE);
		}

		String queueSessionId = jwtTokenUtil.getClaim(token, "queueSessionId", String.class);
		if (!queueSessionId.equals(storedSessionId)) {
			throw new GeneralException(QueueErrorCode.SESSION_REVOKED);	// 다른 화면에서 예매를 이어받은 경우 (이 화면은 종료)
		}
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
