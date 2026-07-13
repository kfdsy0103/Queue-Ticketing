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
import ticketing.domain.concert.scheduleseat.dto.FindDTO;
import ticketing.domain.concert.scheduleseat.dto.OccupyDTO;
import ticketing.domain.concert.scheduleseat.service.ScheduleSeatService;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@RestController
@RequestMapping("/api/v1/schedule-seats")
@RequiredArgsConstructor
public class ScheduleSeatController {

	private final ScheduleSeatService scheduleSeatService;

	/**
	 * 여러 좌석을 5분간 선점(점유)합니다. Redis Lua 스크립트로 all-or-nothing 처리되어,
	 * 다른 사용자가 점유한 좌석이 하나라도 포함되어 있으면 전체 실패합니다.
	 */
	@PostMapping("/occupy")
	public CommonResponse<OccupyDTO.Response> occupy(@Valid @RequestBody OccupyDTO.Request request, @RequestParam Long userId) {
		log.info("[ScheduleSeatController] occupy() 호출 - userId: {}, scheduleSeatIds: {}", userId, request.getScheduleSeatIds());
		OccupyDTO.Result result = scheduleSeatService.occupy(request.toCommand(userId));
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}

	@GetMapping("/{scheduleSeatId}")
	public CommonResponse<FindDTO.Response> find(@PathVariable Long scheduleSeatId, @RequestParam Long userId) {
		log.info("[ScheduleSeatController] find() 호출 - userId: {}, scheduleSeatId: {}", userId, scheduleSeatId);
		FindDTO.Result result = scheduleSeatService.find(FindDTO.Command.builder().scheduleSeatId(scheduleSeatId).userId(userId).build());
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}

	@GetMapping
	public CommonResponse<FindAllDTO.Response> findAll(@RequestParam Long userId) {
		log.info("[ScheduleSeatController] findAll() 호출 - userId: {}", userId);
		FindAllDTO.Result result = scheduleSeatService.findAll(FindAllDTO.Command.builder().userId(userId).build());
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}
}
