package ticketing.domain.concert.scheduleseat.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.scheduleseat.constants.ScheduleSeatRedisKeys;
import ticketing.domain.concert.scheduleseat.dto.FindAllDTO;
import ticketing.domain.concert.scheduleseat.dto.FindDTO;
import ticketing.domain.concert.scheduleseat.dto.OccupyDTO;
import ticketing.domain.concert.scheduleseat.entity.ScheduleSeat;
import ticketing.domain.concert.scheduleseat.exception.ScheduleSeatErrorCode;
import ticketing.domain.concert.scheduleseat.repository.ScheduleSeatRepository;
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
	private static final RedisScript<Long> VERIFY_OCCUPY_SCRIPT =
		RedisScript.of(new ClassPathResource("luaScripts/verify-occupy.lua"), Long.class);

	private final ScheduleSeatRepository scheduleSeatRepository;
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

		// 토큰에 기록된 userId와 요청자 userId 일치 검사
		Long tokenUserId = jwtTokenUtil.getClaim(command.getToken(), "userId", Long.class);
		if (!command.getUserId().equals(tokenUserId)) {
			throw new GeneralException(GeneralErrorCode.FORBIDDEN);
		}

		// 유저가 해당 회차의 대기열을 통과(Active)했고, 최신 화면(sessionId)의 요청인지 확인
		Long concertScheduleId = scheduleSeats.getFirst().getConcertSchedule().getId();
		String activeKey = QueueRedisKeys.activeKey(concertScheduleId);
		String storedSessionId = redisUtil.hGet(activeKey, command.getUserId().toString());
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

	public FindDTO.Result find(FindDTO.Command command) {
		ScheduleSeat scheduleSeat = scheduleSeatRepository.findById(command.getScheduleSeatId())
			.orElseThrow(() -> new GeneralException(ScheduleSeatErrorCode.SCHEDULE_SEAT_NOT_FOUND));

		// 토큰에 기록된 userId와 요청자 userId 일치 검사
		Long tokenUserId = jwtTokenUtil.getClaim(command.getToken(), "userId", Long.class);
		if (!command.getUserId().equals(tokenUserId)) {
			throw new GeneralException(GeneralErrorCode.FORBIDDEN);
		}

		// 유저가 해당 회차의 대기열을 통과(Active)했고, 최신 화면(sessionId)의 요청인지 확인
		Long concertScheduleId = scheduleSeat.getConcertSchedule().getId();
		String storedSessionId = redisUtil.hGet(QueueRedisKeys.activeKey(concertScheduleId), command.getUserId().toString());
		if (storedSessionId == null) {
			throw new GeneralException(QueueErrorCode.NOT_ACTIVE);
		}
		String queueSessionId = jwtTokenUtil.getClaim(command.getToken(), "queueSessionId", String.class);
		if (!queueSessionId.equals(storedSessionId)) {
			throw new GeneralException(QueueErrorCode.SESSION_REVOKED);	// 다른 화면에서 예매를 이어받은 경우 (이 화면은 종료)
		}

		Long occupiedByMe = redisUtil.execute(
			VERIFY_OCCUPY_SCRIPT,
			List.of(ScheduleSeatRedisKeys.occupyKey(scheduleSeat.getId())),
			command.getUserId().toString()
		);

		return FindDTO.Result.builder()
			.scheduleSeatId(scheduleSeat.getId())
			.concertScheduleId(scheduleSeat.getConcertSchedule().getId())
			.seatId(scheduleSeat.getSeat().getId())
			.seatStatus(scheduleSeat.getSeatStatus())
			.occupiedByMe(occupiedByMe != null && occupiedByMe == 1L)
			.build();
	}

	public FindAllDTO.Result findAll(FindAllDTO.Command command) {

		// 토큰에 기록된 userId와 요청자 userId 일치 검사
		Long tokenUserId = jwtTokenUtil.getClaim(command.getToken(), "userId", Long.class);
		if (!command.getUserId().equals(tokenUserId)) {
			throw new GeneralException(GeneralErrorCode.FORBIDDEN);
		}

		// 유저가 해당 회차의 대기열을 통과(Active)했고, 최신 화면(sessionId)의 요청인지 확인
		String storedSessionId = redisUtil.hGet(QueueRedisKeys.activeKey(command.getConcertScheduleId()), command.getUserId().toString());
		if (storedSessionId == null) {
			throw new GeneralException(QueueErrorCode.NOT_ACTIVE);
		}
		String queueSessionId = jwtTokenUtil.getClaim(command.getToken(), "queueSessionId", String.class);
		if (!queueSessionId.equals(storedSessionId)) {
			throw new GeneralException(QueueErrorCode.SESSION_REVOKED);	// 다른 화면에서 예매를 이어받은 경우 (이 화면은 종료)
		}

		List<FindDTO.Result> scheduleSeats = scheduleSeatRepository.findAllByConcertScheduleId(command.getConcertScheduleId()).stream()
			.map(scheduleSeat -> {
				Long occupiedByMe = redisUtil.execute(
					VERIFY_OCCUPY_SCRIPT,
					List.of(ScheduleSeatRedisKeys.occupyKey(scheduleSeat.getId())),
					command.getUserId().toString()
				);

				return FindDTO.Result.builder()
					.scheduleSeatId(scheduleSeat.getId())
					.concertScheduleId(scheduleSeat.getConcertSchedule().getId())
					.seatId(scheduleSeat.getSeat().getId())
					.seatStatus(scheduleSeat.getSeatStatus())
					.occupiedByMe(occupiedByMe != null && occupiedByMe == 1L)
					.build();
			})
			.toList();

		return FindAllDTO.Result.builder()
			.scheduleSeats(scheduleSeats)
			.build();
	}
}
