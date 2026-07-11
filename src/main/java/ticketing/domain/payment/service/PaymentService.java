package ticketing.domain.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.domain.order.order.entity.Order;
import ticketing.domain.order.order.exception.OrderErrorCode;
import ticketing.domain.order.order.repository.OrderRepository;
import ticketing.domain.payment.client.KakaoPayApiClient;
import ticketing.domain.payment.client.dto.KakaoPayReady;
import ticketing.domain.payment.dto.ReadyDTO;
import ticketing.domain.payment.entity.Payment;
import ticketing.domain.payment.exception.PaymentErrorCode;
import ticketing.domain.payment.repository.PaymentRepository;
import ticketing.global.apiPayload.code.GeneralErrorCode;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

	private final OrderRepository orderRepository;
	private final PaymentRepository paymentRepository;
	private final KakaoPayApiClient kakaoPayApiClient;

	/**
	 * 결제 준비(ready)를 처리합니다.
	 * orderId를 받아 주문 고유 번호 tid를 생성합니다.
	 */
	@Transactional
	public ReadyDTO.Result ready(ReadyDTO.Command command) {

		// 생성한 주문 조회
		Order order = orderRepository.findById(command.getOrderId())
			.orElseThrow(() -> new GeneralException(OrderErrorCode.ORDER_NOT_FOUND));

		// 주문 생성자와 API 호출자 일치 검사
		if (!order.getUser().getId().equals(command.getUserId())) {
			throw new GeneralException(GeneralErrorCode.FORBIDDEN);
		}

		// 카카오페이 쪽에 주문 고유 번호를 생성하는 준비 단계입니다.
		KakaoPayReady kakaoPayReadyResult = kakaoPayApiClient.ready(order.getId());

		Payment payment = Payment.builder()
			.order(order)
			.tid(kakaoPayReadyResult.getTid())
			.redirectUrl(kakaoPayReadyResult.getRedirectUrl())
			.totalPrice(order.getTotalPrice())
			.build();
		paymentRepository.save(payment);

		return ReadyDTO.Result.builder()
			.redirectUrl(kakaoPayReadyResult.getRedirectUrl())
			.build();
	}
}
