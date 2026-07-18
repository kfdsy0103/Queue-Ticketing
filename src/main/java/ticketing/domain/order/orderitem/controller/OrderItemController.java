package ticketing.domain.order.orderitem.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.order.orderitem.dto.CancelPartialDTO;
import ticketing.domain.order.orderitem.service.OrderItemService;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderItemController {

	private final OrderItemService orderItemService;

	@DeleteMapping("/{orderId}/cancel-partial")
	public CommonResponse<CancelPartialDTO.Response> cancelPartial(@Valid @RequestBody CancelPartialDTO.Request request, @RequestParam Long userId) {
		log.info("[OrderItemController] cancelPartial() 호출 - userId: {}, orderItemId: {}", request.getOrderItemId(), userId);
		CancelPartialDTO.Result result = orderItemService.cancelPartial(request.toCommand(userId));
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}
}
