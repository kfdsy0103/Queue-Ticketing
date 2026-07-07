package ticketing.global.apiPayload.exception;

import lombok.Getter;
import ticketing.global.apiPayload.code.BaseCode;

/**
 * 비즈니스 로직 실행 중 발생하는 예외를 표현하는 클래스입니다.
 * {@link BaseCode}를 포함하여, 예외에 대한 구체적인 정보를 제공합니다.
 */
@Getter
public class GeneralException extends RuntimeException {
	private final BaseCode code;

	public GeneralException(BaseCode code) {
		super(code.getMessage());
		this.code = code;
	}
}
