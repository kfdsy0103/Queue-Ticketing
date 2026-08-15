package ticketing.domain.concert.concertschedule.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.concertschedule.dto.FindAllDTO;
import ticketing.domain.concert.concertschedule.dto.FindDTO;
import ticketing.domain.concert.concertschedule.service.ConcertScheduleQueryService;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@RestController
@RequestMapping("/api/v1/concerts/{concertId}/concert-schedules")
@RequiredArgsConstructor
public class ConcertScheduleController {

	private final ConcertScheduleQueryService concertScheduleQueryService;

	@GetMapping("/{concertScheduleId}")
	public CommonResponse<FindDTO.Response> find(@PathVariable Long concertId, @PathVariable Long concertScheduleId, @RequestParam Long userId) {
		log.info("[ConcertScheduleController] find() 호출 - userId: {}, concertId: {}, concertScheduleId: {}", userId, concertId, concertScheduleId);
		FindDTO.Result result = concertScheduleQueryService.find(FindDTO.Command.of(concertScheduleId));
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}

	@GetMapping
	public CommonResponse<FindAllDTO.Response> findAll(@PathVariable Long concertId, @RequestParam Long userId) {
		log.info("[ConcertScheduleController] findAll() 호출 - userId: {}, concertId: {}", userId, concertId);
		FindAllDTO.Result result = concertScheduleQueryService.findAll(FindAllDTO.Command.of(concertId));
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}
}
