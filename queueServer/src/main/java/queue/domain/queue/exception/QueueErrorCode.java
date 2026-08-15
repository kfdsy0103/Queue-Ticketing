package queue.domain.queue.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import queue.global.apiPayload.code.BaseCode;

@Getter
@RequiredArgsConstructor
public enum QueueErrorCode implements BaseCode {

	SESSION_REVOKED(HttpStatus.FORBIDDEN, "QUEUE_403_02", "다른 화면에서 이어받아 세션이 종료되었습니다."),
	NOT_IN_QUEUE(HttpStatus.NOT_FOUND, "QUEUE_404_01", "대기열에 등록되지 않은 회원입니다."),
	ALREADY_JOINED(HttpStatus.CONFLICT, "QUEUE_409_02", "이미 대기열에 참여 중입니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
