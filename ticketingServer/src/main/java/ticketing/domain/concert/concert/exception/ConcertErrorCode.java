package ticketing.domain.concert.concert.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.global.apiPayload.code.BaseCode;

@Getter
@RequiredArgsConstructor
public enum ConcertErrorCode implements BaseCode {

	CONCERT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONCERT_404_01", "존재하지 않는 콘서트입니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
