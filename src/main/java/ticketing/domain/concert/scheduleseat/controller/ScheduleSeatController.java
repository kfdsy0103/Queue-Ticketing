package ticketing.domain.concert.scheduleseat.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.scheduleseat.dto.FindAllDTO;
import ticketing.domain.concert.scheduleseat.dto.FindOccupyDTO;
import ticketing.domain.concert.scheduleseat.dto.FindRemainingDTO;
import ticketing.domain.concert.scheduleseat.dto.OccupyDTO;
import ticketing.domain.concert.scheduleseat.service.ScheduleSeatService;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ScheduleSeatController {

	private final ScheduleSeatService scheduleSeatService;

	/**
	 * 여러 좌석을 5분간 선점(점유)합니다. Redis Lua 스크립트로 all-or-nothing 처리되어,
	 * 다른 사용자가 점유한 좌석이 하나라도 포함되어 있으면 전체 실패합니다.
	 */
	@PostMapping("/concert-schedules/{concertScheduleId}/schedule-seats/occupy")
	public CommonResponse<OccupyDTO.Response> occupy(@PathVariable Long concertScheduleId,
		@Valid @RequestBody OccupyDTO.Request request, @RequestParam Long userId) {
		log.info("[ScheduleSeatController] occupy() 호출 - userId: {}, concertScheduleId: {}, scheduleSeatIds: {}",
			userId, concertScheduleId, request.getScheduleSeatIds());
		OccupyDTO.Result result = scheduleSeatService.occupy(request.toCommand(userId));
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}

	/**
	 * 특정 회차에 대한 좌석 정보를 모두 조회합니다.
	 * 좌석의 상태는 다음 3가지로 분류되고, 이 중 AVAILABLE만 예약 가능합니다.
	 *     - AVAILABLE : 점유되거나 판매되지 않음
	 *     - OCCUPIED : 점유된 상태
	 *     - SOLD : 팔린 상태
	 */
	@GetMapping("/concert-schedules/{concertScheduleId}/schedule-seats")
	public CommonResponse<FindAllDTO.Response> findAll(@PathVariable Long concertScheduleId,
		@RequestParam Long userId, @RequestParam String token) {
		log.info("[ScheduleSeatController] findAll() 호출 - userId: {}, concertScheduleId: {}", userId, concertScheduleId);
		FindAllDTO.Result result = scheduleSeatService.findAll(FindAllDTO.Command.of(userId, concertScheduleId, token));
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}

	/**
	 * 특정 회차의 좌석 등급별 잔여 좌석 개수를 조회합니다.
	 */
	@GetMapping("/concert-schedules/{concertScheduleId}/schedule-seats/remaining")
	public CommonResponse<FindRemainingDTO.Response> findRemaining(
		@PathVariable Long concertScheduleId,
		@RequestParam Long userId
	) {
		log.info("[ScheduleSeatController] findRemaining() 호출 - userId: {}, concertScheduleId: {}", userId, concertScheduleId);
		FindRemainingDTO.Result result = scheduleSeatService.findRemaining(FindRemainingDTO.Command.of(concertScheduleId));
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}

	/**
	 * 사용자가 현재 점유 중인 좌석을 회차 구분 없이 모두 조회합니다.
	 * 항목마다 어느 회차의 좌석인지와 점유 만료 시각이 함께 내려갑니다.
	 */
	@GetMapping("/users/occupy")
	public CommonResponse<FindOccupyDTO.Response> findMyOccupiedSeats(@RequestParam Long userId) {
		log.info("[ScheduleSeatController] findMyOccupiedSeats() 호출 - userId: {}", userId);
		FindOccupyDTO.Result result = scheduleSeatService.findMyOccupiedSeats(FindOccupyDTO.Command.of(userId));
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}
}
