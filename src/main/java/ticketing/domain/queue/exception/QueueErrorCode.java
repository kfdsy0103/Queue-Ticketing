package ticketing.domain.queue.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.global.apiPayload.code.BaseCode;

@Getter
@RequiredArgsConstructor
public enum QueueErrorCode implements BaseCode {

	INVALID_ENTER_TYPE(HttpStatus.BAD_REQUEST, "QUEUE_400_01", "유효하지 않은 대기열 입장 타입입니다."),
	NOT_IN_QUEUE(HttpStatus.NOT_FOUND, "QUEUE_404_01", "대기열에 등록되지 않은 회원입니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
