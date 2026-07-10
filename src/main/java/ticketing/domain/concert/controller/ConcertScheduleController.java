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
import ticketing.domain.concert.dto.ConcertScheduleDTO;
import ticketing.domain.concert.service.ConcertScheduleService;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@RestController
@RequestMapping("/api/v1/concert-schedules")
@RequiredArgsConstructor
public class ConcertScheduleController {

	private final ConcertScheduleService concertScheduleService;

	@PostMapping
	public CommonResponse<?> create(@Valid @RequestBody ConcertScheduleDTO.Request request) {
		log.info("[ConcertScheduleController] create() 호출");
		ConcertScheduleDTO.Response response = concertScheduleService.create(request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.CREATED, response);
	}

	@GetMapping("/{id}")
	public CommonResponse<?> getById(@PathVariable Long id) {
		log.info("[ConcertScheduleController] getById() 호출 - id: {}", id);
		ConcertScheduleDTO.Response response = concertScheduleService.getById(id);
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, response);
	}

	@GetMapping
	public CommonResponse<?> getAll() {
		log.info("[ConcertScheduleController] getAll() 호출");
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, concertScheduleService.getAll());
	}

	@PutMapping("/{id}")
	public CommonResponse<?> update(@PathVariable Long id, @Valid @RequestBody ConcertScheduleDTO.Request request) {
		log.info("[ConcertScheduleController] update() 호출 - id: {}", id);
		ConcertScheduleDTO.Response response = concertScheduleService.update(id, request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, response);
	}

	@DeleteMapping("/{id}")
	public CommonResponse<?> delete(@PathVariable Long id) {
		log.info("[ConcertScheduleController] delete() 호출 - id: {}", id);
		concertScheduleService.delete(id);
		return CommonResponse.onSuccess(GeneralSuccessCode.NO_CONTENT, null);
	}
}
