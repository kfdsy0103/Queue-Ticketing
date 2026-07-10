package ticketing.domain.venue.seatgrade.controller;

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
import ticketing.domain.venue.seatgrade.dto.CreateDTO;
import ticketing.domain.venue.seatgrade.dto.UpdateDTO;
import ticketing.domain.venue.seatgrade.service.SeatGradeService;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@RestController
@RequestMapping("/api/v1/seat-grades")
@RequiredArgsConstructor
public class SeatGradeController {

	private final SeatGradeService seatGradeService;

	@PostMapping
	public CommonResponse<?> create(@Valid @RequestBody CreateDTO.Request request) {
		log.info("[SeatGradeController] create() 호출");
		CreateDTO.Response response = seatGradeService.create(request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.CREATED, response);
	}

	@GetMapping("/{id}")
	public CommonResponse<?> getById(@PathVariable Long id) {
		log.info("[SeatGradeController] getById() 호출 - id: {}", id);
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, seatGradeService.getById(id));
	}

	@GetMapping
	public CommonResponse<?> getAll() {
		log.info("[SeatGradeController] getAll() 호출");
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, seatGradeService.getAll());
	}

	@PutMapping("/{id}")
	public CommonResponse<?> update(@PathVariable Long id, @Valid @RequestBody UpdateDTO.Request request) {
		log.info("[SeatGradeController] update() 호출 - id: {}", id);
		UpdateDTO.Response response = seatGradeService.update(id, request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, response);
	}

	@DeleteMapping("/{id}")
	public CommonResponse<?> delete(@PathVariable Long id) {
		log.info("[SeatGradeController] delete() 호출 - id: {}", id);
		seatGradeService.delete(id);
		return CommonResponse.onSuccess(GeneralSuccessCode.NO_CONTENT, null);
	}
}
