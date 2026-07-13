package ticketing.domain.venue.seat.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.venue.seat.dto.CreateDTO;
import ticketing.domain.venue.seat.dto.FindAllDTO;
import ticketing.domain.venue.seat.service.SeatCommandService;
import ticketing.domain.venue.seat.service.SeatQueryService;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@RestController
@RequestMapping("/api/v1/seats")
@RequiredArgsConstructor
public class SeatController {

	private final SeatCommandService seatCommandService;
	private final SeatQueryService seatQueryService;

	@PostMapping
	public CommonResponse<CreateDTO.Response> create(@Valid @RequestBody CreateDTO.Request request, @RequestParam Long userId) {
		log.info("[SeatController] create() 호출 - userId: {}", userId);
		CreateDTO.Result result = seatCommandService.create(request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.CREATED, result.toResponse());
	}

	@GetMapping
	public CommonResponse<FindAllDTO.Response> findAll(@RequestParam Long userId) {
		log.info("[SeatController] findAll() 호출 - userId: {}", userId);
		FindAllDTO.Result result = seatQueryService.findAll();
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}
}
