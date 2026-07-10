package ticketing.domain.concert.scheduleprice.controller;

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
import ticketing.domain.concert.scheduleprice.dto.CreateDTO;
import ticketing.domain.concert.scheduleprice.dto.UpdateDTO;
import ticketing.domain.concert.scheduleprice.service.SchedulePriceService;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@RestController
@RequestMapping("/api/v1/schedule-prices")
@RequiredArgsConstructor
public class SchedulePriceController {

	private final SchedulePriceService schedulePriceService;

	@PostMapping
	public CommonResponse<?> create(@Valid @RequestBody CreateDTO.Request request) {
		log.info("[SchedulePriceController] create() 호출");
		CreateDTO.Response response = schedulePriceService.create(request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.CREATED, response);
	}

	@GetMapping("/{id}")
	public CommonResponse<?> getById(@PathVariable Long id) {
		log.info("[SchedulePriceController] getById() 호출 - id: {}", id);
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, schedulePriceService.getById(id));
	}

	@GetMapping
	public CommonResponse<?> getAll() {
		log.info("[SchedulePriceController] getAll() 호출");
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, schedulePriceService.getAll());
	}

	@PutMapping("/{id}")
	public CommonResponse<?> update(@PathVariable Long id, @Valid @RequestBody UpdateDTO.Request request) {
		log.info("[SchedulePriceController] update() 호출 - id: {}", id);
		UpdateDTO.Response response = schedulePriceService.update(id, request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, response);
	}

	@DeleteMapping("/{id}")
	public CommonResponse<?> delete(@PathVariable Long id) {
		log.info("[SchedulePriceController] delete() 호출 - id: {}", id);
		schedulePriceService.delete(id);
		return CommonResponse.onSuccess(GeneralSuccessCode.NO_CONTENT, null);
	}
}
