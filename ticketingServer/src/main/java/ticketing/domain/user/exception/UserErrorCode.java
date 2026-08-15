package ticketing.domain.user.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.global.apiPayload.code.BaseCode;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements BaseCode {

	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404_01", "존재하지 않는 회원입니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
