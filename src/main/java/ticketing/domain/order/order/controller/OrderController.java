package ticketing.domain.order.order.controller;

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
import ticketing.domain.order.order.dto.CreateDTO;
import ticketing.domain.order.order.dto.UpdateDTO;
import ticketing.domain.order.order.service.OrderService;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

	private final OrderService orderService;

	@PostMapping
	public CommonResponse<?> create(@Valid @RequestBody CreateDTO.Request request) {
		log.info("[OrderController] create() 호출");
		CreateDTO.Response response = orderService.create(request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.CREATED, response);
	}

	@GetMapping("/{id}")
	public CommonResponse<?> getById(@PathVariable Long id) {
		log.info("[OrderController] getById() 호출 - id: {}", id);
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, orderService.getById(id));
	}

	@GetMapping
	public CommonResponse<?> getAll() {
		log.info("[OrderController] getAll() 호출");
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, orderService.getAll());
	}

	@PutMapping("/{id}")
	public CommonResponse<?> update(@PathVariable Long id, @Valid @RequestBody UpdateDTO.Request request) {
		log.info("[OrderController] update() 호출 - id: {}", id);
		UpdateDTO.Response response = orderService.update(id, request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, response);
	}

	@DeleteMapping("/{id}")
	public CommonResponse<?> delete(@PathVariable Long id) {
		log.info("[OrderController] delete() 호출 - id: {}", id);
		orderService.delete(id);
		return CommonResponse.onSuccess(GeneralSuccessCode.NO_CONTENT, null);
	}
}
