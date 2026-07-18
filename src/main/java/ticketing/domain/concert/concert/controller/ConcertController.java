package ticketing.domain.concert.concert.controller;

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
import ticketing.domain.concert.concert.dto.CreateDTO;
import ticketing.domain.concert.concert.dto.FindAllDTO;
import ticketing.domain.concert.concert.dto.FindDTO;
import ticketing.domain.concert.concert.service.ConcertService;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@RestController
@RequestMapping("/api/v1/concerts")
@RequiredArgsConstructor
public class ConcertController {

	private final ConcertService concertService;

	@PostMapping
	public CommonResponse<CreateDTO.Response> create(@Valid @RequestBody CreateDTO.Request request, @RequestParam Long userId) {
		log.info("[ConcertController] create() 호출 - userId: {}", userId);
		CreateDTO.Result result = concertService.create(request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.CREATED, result.toResponse());
	}

	@GetMapping("/{concertId}")
	public CommonResponse<FindDTO.Response> find(@PathVariable Long concertId, @RequestParam Long userId) {
		log.info("[ConcertController] find() 호출 - userId: {}, concertId: {}", userId, concertId);
		FindDTO.Result result = concertService.find(FindDTO.Command.of(concertId));
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}

	@GetMapping
	public CommonResponse<FindAllDTO.Response> findAll(@RequestParam Long userId) {
		log.info("[ConcertController] findAll() 호출 - userId: {}", userId);
		FindAllDTO.Result result = concertService.findAll();
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}
}
