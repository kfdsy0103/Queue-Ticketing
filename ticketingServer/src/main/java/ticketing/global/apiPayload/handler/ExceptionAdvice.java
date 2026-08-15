package ticketing.global.apiPayload.handler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import ticketing.global.apiPayload.CommonResponse;
import ticketing.global.apiPayload.code.BaseCode;
import ticketing.global.apiPayload.code.GeneralErrorCode;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@RestControllerAdvice(annotations = {RestController.class})
public class ExceptionAdvice extends ResponseEntityExceptionHandler {

	/**
	 * @Valid + @RequestBody DTO 검증 실패 시 발생하는
	 * {@link MethodArgumentNotValidException}을 처리합니다.
	 * 모든 필드 에러를 수집하여 Map으로 반환합니다.
	 */
	@Override
	public ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();

		e.getBindingResult().getFieldErrors()
			.forEach(fieldError -> {
				String fieldName = fieldError.getField();
				String errorMessage = Optional.ofNullable(fieldError.getDefaultMessage()).orElse("");
				errors.merge(fieldName, errorMessage,
					(existingErrorMessage, newErrorMessage) -> existingErrorMessage + ", " + newErrorMessage);
			});

		log.warn("MethodArgumentNotValidException: {}", errors);

		return ResponseEntity
			.status(status)
			.headers(headers)
			.body(CommonResponse.onFailure(GeneralErrorCode.INVALID_INPUT_VALUE, errors));
	}

	/**
	 * Jackson이 JSON Body 파싱 실패 시 발생하는 {@link HttpMessageNotReadableException}을 처리합니다.
	 * 커스텀 Deserializer에서 {@link GeneralException}이 발생한 경우 해당 코드로 응답하고,
	 * 그 외 잘못된 JSON 형식이나 Enum 불일치 등은 INVALID_INPUT_VALUE로 응답합니다.
	 */
	@Override
	public ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Throwable cause = e.getMostSpecificCause();

		if (cause instanceof GeneralException ge) {
			BaseCode code = ge.getCode();
			log.warn("Enum Deserialization Custom Error: {}", code.getMessage());
			return ResponseEntity
				.status(code.getHttpStatus())
				.headers(headers)
				.body(CommonResponse.onFailure(code, null));
		}

		log.warn("HttpMessageNotReadableException: {}", e.getMessage());
		return ResponseEntity
			.status(status)
			.headers(headers)
			.body(CommonResponse.onFailure(GeneralErrorCode.INVALID_INPUT_VALUE, null));
	}

	/**
	 * @Validated 클래스에서 @RequestParam, @PathVariable 검증 실패 시
	 * 발생하는 {@link ConstraintViolationException}을 처리합니다.
	 * 예: @Min, @NotBlank 등이 붙은 PathVariable에 잘못된 값이 들어온 경우
	 */
	@ExceptionHandler
	public ResponseEntity<Object> validation(ConstraintViolationException e) {
		String errorMessages = e.getConstraintViolations().stream()
			.map(ConstraintViolation::getMessage)
			.collect(Collectors.joining(", "));

		log.warn("ConstraintViolationException: {}", errorMessages);

		return ResponseEntity
			.status(GeneralErrorCode.INVALID_INPUT_VALUE.getHttpStatus())
			.body(CommonResponse.onFailure(GeneralErrorCode.INVALID_INPUT_VALUE, null));
	}

	/**
	 * {@link GeneralException}을 상속한 커스텀 비즈니스 예외를 처리합니다.
	 * 예외에 담긴 {@link BaseCode}에서 HTTP 상태와 메시지를 추출하여 응답합니다.
	 */
	@ExceptionHandler(value = GeneralException.class)
	public ResponseEntity<Object> onThrowException(GeneralException generalException) {
		BaseCode code = generalException.getCode();
		log.warn("GeneralException: {} - {}", code.getCode(), code.getMessage());

		return ResponseEntity
			.status(code.getHttpStatus())
			.body(CommonResponse.onFailure(code, null));
	}

	/**
	 * @PathVariable, @RequestParam에 선언된 타입과 실제 입력값의 타입이 다를 때
	 * 발생하는 {@link MethodArgumentTypeMismatchException}을 처리합니다.
	 * 예: Long 타입 PathVariable에 문자열이 들어온 경우
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<Object> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
		log.warn("MethodArgumentTypeMismatch param={} value={} requiredType={}", e.getName(), e.getValue(),
			e.getRequiredType());

		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(CommonResponse.onFailure(GeneralErrorCode.INVALID_TYPE_VALUE, null));
	}

	/**
	 * 위의 핸들러에서 처리되지 않은 모든 예외의 최종 핸들러입니다.
	 * 예외 상세 메시지는 클라이언트에 노출하지 않고 서버 로그에만 기록합니다.
	 */
	@ExceptionHandler
	public ResponseEntity<Object> exception(Exception e) {
		log.error("Unhandled exception occurred", e);
		return ResponseEntity
			.status(GeneralErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
			.body(CommonResponse.onFailure(GeneralErrorCode.INTERNAL_SERVER_ERROR, null));
	}
}