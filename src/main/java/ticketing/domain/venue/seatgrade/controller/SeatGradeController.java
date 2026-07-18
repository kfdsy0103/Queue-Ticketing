package ticketing.domain.venue.seatgrade.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.venue.seatgrade.dto.FindAllDTO;
import ticketing.domain.venue.seatgrade.dto.FindDTO;
import ticketing.domain.venue.seatgrade.service.SeatGradeQueryService;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@RestController
@RequestMapping("/api/v1/seat-grades")
@RequiredArgsConstructor
public class SeatGradeController {

	private final SeatGradeQueryService seatGradeQueryService;

	@GetMapping("/{seatGradeId}")
	public CommonResponse<FindDTO.Response> find(@PathVariable Long seatGradeId, @RequestParam Long userId) {
		log.info("[SeatGradeController] find() 호출 - userId: {}, seatGradeId: {}", userId, seatGradeId);
		FindDTO.Result result = seatGradeQueryService.find(FindDTO.Command.of(seatGradeId));
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}

	@GetMapping
	public CommonResponse<FindAllDTO.Response> findAll(@RequestParam Long userId) {
		log.info("[SeatGradeController] findAll() 호출 - userId: {}", userId);
		FindAllDTO.Result result = seatGradeQueryService.findAll();
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}
}
