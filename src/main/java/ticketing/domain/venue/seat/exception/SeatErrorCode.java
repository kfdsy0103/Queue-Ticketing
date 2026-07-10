package ticketing.domain.venue.seat.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.global.apiPayload.code.BaseCode;

@Getter
@RequiredArgsConstructor
public enum SeatErrorCode implements BaseCode {

	SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "SEAT_404_01", "존재하지 않는 좌석입니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
