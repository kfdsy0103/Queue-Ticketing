package ticketing.domain.payment.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.global.apiPayload.code.BaseCode;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements BaseCode {

	PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_404_01", "결제 준비 정보를 찾을 수 없습니다."),
	ALREADY_PAID(HttpStatus.CONFLICT, "PAYMENT_409_01", "이미 결제가 완료된 주문입니다."),
	AMOUNT_MISMATCH(HttpStatus.CONFLICT, "PAYMENT_409_02", "결제 금액이 주문 금액과 일치하지 않습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
