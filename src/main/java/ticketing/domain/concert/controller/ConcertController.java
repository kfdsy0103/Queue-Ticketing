package ticketing.domain.concert.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.concert.dto.ConcertDTO;
import ticketing.domain.concert.service.ConcertService;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@RestController
@RequestMapping("/api/v1/concerts")
@RequiredArgsConstructor
public class ConcertController {

	private final ConcertService concertService;

	@PostMapping
	public CommonResponse<?> create(@Valid @RequestBody ConcertDTO.Request request) {
		log.info("[ConcertController] create() 호출");
		ConcertDTO.Response response = concertService.create(request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.CREATED, response);
	}

	@GetMapping("/{id}")
	public CommonResponse<?> getById(@PathVariable Long id) {
		log.info("[ConcertController] getById() 호출 - id: {}", id);
		ConcertDTO.Response response = concertService.getById(id);
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, response);
	}

	@GetMapping
	public CommonResponse<?> getAll() {
		log.info("[ConcertController] getAll() 호출");
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, concertService.getAll());
	}

	@PutMapping("/{id}")
	public CommonResponse<?> update(@PathVariable Long id, @Valid @RequestBody ConcertDTO.Request request) {
		log.info("[ConcertController] update() 호출 - id: {}", id);
		ConcertDTO.Response response = concertService.update(id, request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, response);
	}

	@DeleteMapping("/{id}")
	public CommonResponse<?> delete(@PathVariable Long id) {
		log.info("[ConcertController] delete() 호출 - id: {}", id);
		concertService.delete(id);
		return CommonResponse.onSuccess(GeneralSuccessCode.NO_CONTENT, null);
	}
}
