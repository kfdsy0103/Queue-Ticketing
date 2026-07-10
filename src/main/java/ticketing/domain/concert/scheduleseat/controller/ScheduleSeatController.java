package ticketing.domain.concert.scheduleseat.controller;

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
import ticketing.domain.concert.scheduleseat.dto.CreateDTO;
import ticketing.domain.concert.scheduleseat.dto.UpdateDTO;
import ticketing.domain.concert.scheduleseat.service.ScheduleSeatService;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@RestController
@RequestMapping("/api/v1/schedule-seats")
@RequiredArgsConstructor
public class ScheduleSeatController {

	private final ScheduleSeatService scheduleSeatService;

	@PostMapping
	public CommonResponse<?> create(@Valid @RequestBody CreateDTO.Request request) {
		log.info("[ScheduleSeatController] create() 호출");
		CreateDTO.Response response = scheduleSeatService.create(request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.CREATED, response);
	}

	@GetMapping("/{id}")
	public CommonResponse<?> getById(@PathVariable Long id) {
		log.info("[ScheduleSeatController] getById() 호출 - id: {}", id);
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, scheduleSeatService.getById(id));
	}

	@GetMapping
	public CommonResponse<?> getAll() {
		log.info("[ScheduleSeatController] getAll() 호출");
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, scheduleSeatService.getAll());
	}

	@PutMapping("/{id}")
	public CommonResponse<?> update(@PathVariable Long id, @Valid @RequestBody UpdateDTO.Request request) {
		log.info("[ScheduleSeatController] update() 호출 - id: {}", id);
		UpdateDTO.Response response = scheduleSeatService.update(id, request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, response);
	}

	@DeleteMapping("/{id}")
	public CommonResponse<?> delete(@PathVariable Long id) {
		log.info("[ScheduleSeatController] delete() 호출 - id: {}", id);
		scheduleSeatService.delete(id);
		return CommonResponse.onSuccess(GeneralSuccessCode.NO_CONTENT, null);
	}
}
