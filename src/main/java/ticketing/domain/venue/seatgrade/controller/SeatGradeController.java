package ticketing.domain.venue.seatgrade.controller;

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
import ticketing.domain.venue.seatgrade.dto.CreateDTO;
import ticketing.domain.venue.seatgrade.dto.FindAllDTO;
import ticketing.domain.venue.seatgrade.dto.FindDTO;
import ticketing.domain.venue.seatgrade.service.SeatGradeCommandService;
import ticketing.domain.venue.seatgrade.service.SeatGradeQueryService;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@RestController
@RequestMapping("/api/v1/seat-grades")
@RequiredArgsConstructor
public class SeatGradeController {

	private final SeatGradeCommandService seatGradeCommandService;
	private final SeatGradeQueryService seatGradeQueryService;

	@PostMapping
	public CommonResponse<CreateDTO.Response> create(@Valid @RequestBody CreateDTO.Request request) {
		log.info("[SeatGradeController] create() 호출");
		CreateDTO.Result result = seatGradeCommandService.create(request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.CREATED, result.toResponse());
	}

	@GetMapping("/{seatGradeId}")
	public CommonResponse<FindDTO.Response> find(@PathVariable Long seatGradeId, @RequestParam Long userId) {
		log.info("[SeatGradeController] find() 호출 - seatGradeId: {}, userId: {}", seatGradeId, userId);
		FindDTO.Result result = seatGradeQueryService.find(FindDTO.Command.builder().seatGradeId(seatGradeId).build());
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}

	@GetMapping
	public CommonResponse<FindAllDTO.Response> findAll(@RequestParam Long userId) {
		log.info("[SeatGradeController] findAll() 호출 - userId: {}", userId);
		FindAllDTO.Result result = seatGradeQueryService.findAll();
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}
}
