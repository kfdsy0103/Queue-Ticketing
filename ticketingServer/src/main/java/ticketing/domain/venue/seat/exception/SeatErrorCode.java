package ticketing.domain.venue.seat.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.global.apiPayload.code.BaseCode;

@Getter
@RequiredArgsConstructor
public enum SeatErrorCode implements BaseCode {

	;

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
