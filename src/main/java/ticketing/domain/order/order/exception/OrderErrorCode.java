package ticketing.domain.order.order.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.global.apiPayload.code.BaseCode;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements BaseCode {

	ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_404_01", "존재하지 않는 주문입니다."),
	NOT_PENDING_ORDER(HttpStatus.CONFLICT, "ORDER_409_01", "PENDING 상태의 주문이 아닙니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
