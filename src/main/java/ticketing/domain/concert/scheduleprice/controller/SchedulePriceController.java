package ticketing.domain.concert.scheduleprice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.scheduleprice.dto.FindAllDTO;
import ticketing.domain.concert.scheduleprice.dto.FindDTO;
import ticketing.domain.concert.scheduleprice.service.SchedulePriceQueryService;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@RestController
@RequestMapping("/api/v1/schedule-prices")
@RequiredArgsConstructor
public class SchedulePriceController {

	private final SchedulePriceQueryService schedulePriceQueryService;

	@GetMapping("/{schedulePriceId}")
	public CommonResponse<FindDTO.Response> find(@PathVariable Long schedulePriceId, @RequestParam Long userId) {
		log.info("[SchedulePriceController] find() 호출 - userId: {}, schedulePriceId: {}", schedulePriceId, userId);
		FindDTO.Result result = schedulePriceQueryService.find(FindDTO.Command.builder().schedulePriceId(schedulePriceId).build());
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}

	@GetMapping
	public CommonResponse<FindAllDTO.Response> findAll(@RequestParam Long userId) {
		log.info("[SchedulePriceController] findAll() 호출 - userId: {}", userId);
		FindAllDTO.Result result = schedulePriceQueryService.findAll();
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}
}
