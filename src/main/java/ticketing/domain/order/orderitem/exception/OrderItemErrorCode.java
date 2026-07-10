package ticketing.domain.order.orderitem.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.global.apiPayload.code.BaseCode;

@Getter
@RequiredArgsConstructor
public enum OrderItemErrorCode implements BaseCode {

	ORDER_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_ITEM_404_01", "존재하지 않는 주문 항목입니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
