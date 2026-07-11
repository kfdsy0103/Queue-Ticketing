package ticketing.domain.payment.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.payment.dto.ApproveDTO;
import ticketing.domain.payment.dto.ReadyDTO;
import ticketing.domain.payment.service.PaymentService;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.GeneralSuccessCode;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentService paymentService;

	/**
	 * 결제 정보를 생성합니다.
	 * 카카오페이의 경우, ready 단계에서 주는 리다이렉트 경로로 이동해 작업을 완료하면,
	 * 백엔드 쪽 경로의 approve()를 호출할 수 있도록 리다이렉트 해주면서, pgToken 및 approval_url에서 설정한 쿼리 파라미터를 전달해주는 구조.
	 *      "...?pgToken=...&orderId=..."
	 */
	@PostMapping("/ready")
	public CommonResponse<ReadyDTO.Response> ready(@RequestBody @Valid ReadyDTO.Request request) {
		log.info("[PaymentController] ready() 호출 - userId: {}", request.getUserId());
		ReadyDTO.Result result = paymentService.ready(request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.CREATED, result.toResponse());
	}

	/**
	 * 카카오페이 결제 승인 단계로, 실제 돈이 출금되는 단계입니다.
	 * approval_url을 프론트 쪽으로 설정하여, 한번 경유한 뒤 우리 백엔드로 POST 요청을 보내는 상황을 가정.
	 */
	@PostMapping("/approve")
	public CommonResponse<ApproveDTO.Response> approve(@RequestBody @Valid ApproveDTO.Request request) {
		log.info("[PaymentController] approve() 호출 - orderId: {}", request.getOrderId());
		ApproveDTO.Result result = paymentService.approve(request.toCommand());
		return CommonResponse.onSuccess(GeneralSuccessCode.OK, result.toResponse());
	}
}
