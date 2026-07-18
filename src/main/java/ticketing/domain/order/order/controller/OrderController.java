package ticketing.domain.order.order.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.order.order.dto.CancelDTO;
import ticketing.domain.order.order.dto.ConfirmDTO;
import ticketing.domain.order.order.dto.CreateDTO;
import ticketing.domain.order.order.dto.FindDTO;
import ticketing.domain.order.order.service.OrderCommandService;
import ticketing.domain.order.order.service.OrderFacadeService;
import ticketing.domain.order.order.service.OrderQueryService;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

	private final OrderQueryService orderQueryService;
	private final OrderCommandService orderCommandService;
	private final OrderFacadeService orderFacadeService;

	/**
	 * 주문 생성 단계입니다.
	 * 주문하려는 좌석 id List + PaymentMethod(KakaoPay ...)를 인자로 받아, 주문을 생성하고 Payment Client를 호출하여 결제 정보를 생성합니다.
	 * 응답에 포함되는 redirect_url로 프론트에서는 리다이렉트시켜 결제를 처리하면 됩니다.
	 */
	@PostMapping
	public CommonResponse<CreateDTO.Response> create(@Valid @RequestBody CreateDTO.Request request, @RequestParam Long userId) {
		log.info("[OrderController] create() 호출 - userId: {}, scheduleSeatIds: {}", userId, request.getScheduleSeatIds());
		CreateDTO.Result result = orderCommandService.create(request.toCommand(userId));
		return CommonResponse.onSuccess(GeneralSuccessCode.CREATED, result.toResponse());
	}

	/**
	 * 카카오페이 결제 승인 콜백을 받아 주문을 확정합니다.
	 * approval_url을 프론트 쪽으로 설정하여, 한번 경유한 뒤 우리 백엔드로 POST 요청을 보내는 상황을 가정.
	 */
	@PostMapping("/{orderId}/confirm")
	public CommonResponse<ConfirmDTO.Response> confirm(@PathVariable Long orderId, @RequestParam Long userId,
		@Valid @RequestBody ConfirmDTO.Request request) {
		log.info("[OrderController] confirm() 호출 - userId: {}, orderId: {}", userId, orderId);
		ConfirmDTO.Result result = orderFacadeService.confirm(request.toCommand(userId, orderId));
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}

	@GetMapping("/{orderId}")
	public CommonResponse<FindDTO.Response> find(@Valid @RequestBody FindDTO.Request request, @RequestParam Long userId) {
		log.info("[OrderController] find() 호출 - userId: {}, orderId: {}", userId, request.getOrderId());
		FindDTO.Result result = orderQueryService.find(request.toCommand(userId));
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}

	/**
	 * 주문 전체 취소입니다.
	 * 결제 완료된 주문에 속한 모든 좌석을 취소하며, PG 전체 환불과 좌석 상태 복구가 함께 처리됩니다.
	 */
	@DeleteMapping("/{orderId}/cancel-all")
	public CommonResponse<CancelDTO.Response> cancelAll(@PathVariable Long orderId, @RequestParam Long userId) {
		log.info("[OrderController] cancel() 호출 - userId: {}, orderId: {}", orderId, userId);
		CancelDTO.Command command = CancelDTO.Command.builder()
			.orderId(orderId)
			.userId(userId)
			.build();
		CancelDTO.Result result = orderCommandService.cancelAll(command);
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}
}
