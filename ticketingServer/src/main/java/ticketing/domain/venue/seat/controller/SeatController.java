package ticketing.domain.venue.seat.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.venue.seat.dto.FindAllDTO;
import ticketing.domain.venue.seat.service.SeatQueryService;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@RestController
@RequestMapping("/api/v1/venues/{venueId}/seats")
@RequiredArgsConstructor
public class SeatController {

	private final SeatQueryService seatQueryService;

	@GetMapping
	public CommonResponse<FindAllDTO.Response> findAll(@PathVariable Long venueId, @RequestParam Long userId) {
		log.info("[SeatController] findAll() 호출 - userId: {}, venueId: {}", userId, venueId);
		FindAllDTO.Result result = seatQueryService.findAll(FindAllDTO.Command.of(venueId));
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}
}
