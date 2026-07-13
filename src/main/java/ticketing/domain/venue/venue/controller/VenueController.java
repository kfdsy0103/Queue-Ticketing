package ticketing.domain.venue.venue.controller;

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
import ticketing.domain.venue.venue.dto.CreateDTO;
import ticketing.domain.venue.venue.dto.FindAllDTO;
import ticketing.domain.venue.venue.dto.FindDTO;
import ticketing.domain.venue.venue.service.VenueCommandService;
import ticketing.domain.venue.venue.service.VenueQueryService;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@RestController
@RequestMapping("/api/v1/venues")
@RequiredArgsConstructor
public class VenueController {

	private final VenueCommandService venueCommandService;
	private final VenueQueryService venueQueryService;

	@PostMapping
	public CommonResponse<CreateDTO.Response> create(@Valid @RequestBody CreateDTO.Request request, @RequestParam Long userId) {
		log.info("[VenueController] create() 호출 - userId: {}", userId);
		CreateDTO.Result result = venueCommandService.create(request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.CREATED, result.toResponse());
	}

	@GetMapping("/{venueId}")
	public CommonResponse<FindDTO.Response> find(@PathVariable Long venueId, @RequestParam Long userId) {
		log.info("[VenueController] find() 호출 - venueId: {}, userId: {}", venueId, userId);
		FindDTO.Result result = venueQueryService.find(FindDTO.Command.builder().venueId(venueId).build());
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}

	@GetMapping
	public CommonResponse<FindAllDTO.Response> findAll(@RequestParam Long userId) {
		log.info("[VenueController] findAll() 호출 - userId: {}", userId);
		FindAllDTO.Result result = venueQueryService.findAll();
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}
}
