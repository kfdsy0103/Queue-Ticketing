package ticketing.global.apiPayload.code;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 일반적인 에러 코드를 정의하는 열거형입니다.
 * {@link BaseCode}를 구현하여 HTTP 상태, 코드, 메시지를 제공합니다.
 */
@Getter
@AllArgsConstructor
public enum GeneralErrorCode implements BaseCode {

	// 400
	INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON_400_01", "입력값이 올바르지 않습니다."),
	INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "COMMON_400_02", "입력값의 타입이 유효하지 않습니다."),
	MISSING_REQUIRED_FIELD(HttpStatus.BAD_REQUEST, "COMMON_400_03", "필수 입력값이 누락되었습니다."),
	INVALID_FORMAT(HttpStatus.BAD_REQUEST, "COMMON_400_04", "입력값의 형식이 올바르지 않습니다."),

	// 401
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_401_01", "인증이 필요합니다."),
	TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "COMMON_401_02", "토큰이 만료되었습니다."),
	INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "COMMON_401_03", "유효하지 않은 토큰입니다."),

	// 403
	FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_403_01", "접근 권한이 없습니다."),

	// 404
	ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404_01", "해당 리소스를 찾을 수 없습니다."),

	// 405
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_405_01", "지원하지 않는 HTTP 메서드입니다."),

	// 409
	DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "COMMON_409_01", "이미 존재하는 리소스입니다."),

	// 415
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON_415_01", "지원하지 않는 미디어 타입입니다."),

	// 429
	TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "COMMON_429_01", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),

	// 500
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500_01", "서버 내부 오류가 발생했습니다."),
	DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500_02", "데이터베이스 오류가 발생했습니다."),

	// 503
	SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "COMMON_503_01", "서비스를 일시적으로 사용할 수 없습니다.");

	/**
	 * HTTP 상태 코드
	 */
	private final HttpStatus httpStatus;

	/**
	 * 에러 코드
	 */
	private final String code;

	/**
	 * 에러 메시지
	 */
	private final String message;
}
