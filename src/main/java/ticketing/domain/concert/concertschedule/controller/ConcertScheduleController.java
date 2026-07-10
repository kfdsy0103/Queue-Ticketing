package ticketing.domain.concert.concertschedule.controller;

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
import ticketing.domain.concert.concertschedule.dto.CreateDTO;
import ticketing.domain.concert.concertschedule.dto.UpdateDTO;
import ticketing.domain.concert.concertschedule.service.ConcertScheduleService;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@RestController
@RequestMapping("/api/v1/concert-schedules")
@RequiredArgsConstructor
public class ConcertScheduleController {

	private final ConcertScheduleService concertScheduleService;

	@PostMapping
	public CommonResponse<?> create(@Valid @RequestBody CreateDTO.Request request) {
		log.info("[ConcertScheduleController] create() 호출");
		CreateDTO.Response response = concertScheduleService.create(request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.CREATED, response);
	}

	@GetMapping("/{id}")
	public CommonResponse<?> getById(@PathVariable Long id) {
		log.info("[ConcertScheduleController] getById() 호출 - id: {}", id);
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, concertScheduleService.getById(id));
	}

	@GetMapping
	public CommonResponse<?> getAll() {
		log.info("[ConcertScheduleController] getAll() 호출");
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, concertScheduleService.getAll());
	}

	@PutMapping("/{id}")
	public CommonResponse<?> update(@PathVariable Long id, @Valid @RequestBody UpdateDTO.Request request) {
		log.info("[ConcertScheduleController] update() 호출 - id: {}", id);
		UpdateDTO.Response response = concertScheduleService.update(id, request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, response);
	}

	@DeleteMapping("/{id}")
	public CommonResponse<?> delete(@PathVariable Long id) {
		log.info("[ConcertScheduleController] delete() 호출 - id: {}", id);
		concertScheduleService.delete(id);
		return CommonResponse.onSuccess(GeneralSuccessCode.NO_CONTENT, null);
	}
}
