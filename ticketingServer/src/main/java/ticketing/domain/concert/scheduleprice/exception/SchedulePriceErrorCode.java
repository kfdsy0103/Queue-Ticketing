package ticketing.domain.concert.scheduleprice.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.global.apiPayload.code.BaseCode;

@Getter
@RequiredArgsConstructor
public enum SchedulePriceErrorCode implements BaseCode {

	SCHEDULE_PRICE_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_PRICE_404_01", "존재하지 않는 일정 가격입니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
