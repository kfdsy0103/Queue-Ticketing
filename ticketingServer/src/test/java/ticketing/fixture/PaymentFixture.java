package ticketing.fixture;

import java.time.LocalDateTime;

import ticketing.domain.order.order.entity.Order;
import ticketing.domain.payment.client.dto.KakaoPayApproveResponse;
import ticketing.domain.payment.client.dto.KakaoPayOrderResponse;
import ticketing.domain.payment.client.dto.KakaoPayReadyResponse;
import ticketing.domain.payment.client.enums.KakaoPayStatus;
import ticketing.domain.payment.entity.Payment;

public final class PaymentFixture {

	public static final String TID = "tid_test_1";
	public static final String AID = "aid_test_1";
	public static final String REDIRECT_URL = "https://pg.test/redirect?tid=" + TID;
	public static final LocalDateTime APPROVED_AT = LocalDateTime.of(2026, 8, 24, 12, 0);

	private PaymentFixture() {
	}

	public static Payment readyPayment(Long id, Order order, int totalPrice) {
		return Payment.builder()
			.id(id)
			.order(order)
			.method(Payment.PaymentMethod.KAKAO_PAY)
			.status(Payment.PaymentStatus.READY)
			.tid(TID)
			.redirectUrl(REDIRECT_URL)
			.totalPrice(totalPrice)
			.build();
	}

	public static Payment approvedPayment(Long id, Order order, int totalPrice) {
		return Payment.builder()
			.id(id)
			.order(order)
			.method(Payment.PaymentMethod.KAKAO_PAY)
			.status(Payment.PaymentStatus.APPROVED)
			.tid(TID)
			.redirectUrl(REDIRECT_URL)
			.totalPrice(totalPrice)
			.aid(AID)
			.approvedAt(APPROVED_AT)
			.build();
	}

	public static Payment cancelRequestedPayment(Long id, Order order, int totalPrice) {
		return Payment.builder()
			.id(id)
			.order(order)
			.method(Payment.PaymentMethod.KAKAO_PAY)
			.status(Payment.PaymentStatus.CANCEL_REQUESTED)
			.tid(TID)
			.redirectUrl(REDIRECT_URL)
			.totalPrice(totalPrice)
			.aid(AID)
			.approvedAt(APPROVED_AT)
			.build();
	}

	public static KakaoPayReadyResponse readyResponse() {
		return KakaoPayReadyResponse.builder()
			.tid(TID)
			.redirectUrl(REDIRECT_URL)
			.build();
	}

	public static KakaoPayApproveResponse approveResponse(int amount) {
		return KakaoPayApproveResponse.builder()
			.aid(AID)
			.tid(TID)
			.paymentMethodType("MONEY")
			.amount(amount)
			.approvedAt(APPROVED_AT)
			.build();
	}

	public static KakaoPayOrderResponse orderResponse(KakaoPayStatus status, int amount) {
		return KakaoPayOrderResponse.builder()
			.tid(TID)
			.aid(AID)
			.status(status)
			.paymentMethodType("MONEY")
			.amount(amount)
			.approvedAt(APPROVED_AT)
			.build();
	}
}
