package ticketing.domain.queue.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.global.apiPayload.code.BaseCode;

@Getter
@RequiredArgsConstructor
public enum QueueErrorCode implements BaseCode {

	NOT_ACTIVE(HttpStatus.FORBIDDEN, "QUEUE_403_01", "대기열 입장 처리가 완료되지 않았습니다."),
	SESSION_REVOKED(HttpStatus.FORBIDDEN, "QUEUE_403_02", "다른 화면에서 이어받아 세션이 종료되었습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
