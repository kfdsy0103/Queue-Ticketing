package ticketing.domain.payment.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
}
