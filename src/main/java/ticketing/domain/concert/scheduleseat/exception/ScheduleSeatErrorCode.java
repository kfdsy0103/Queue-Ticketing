package ticketing.domain.concert.scheduleseat.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.global.apiPayload.code.BaseCode;

@Getter
@RequiredArgsConstructor
public enum ScheduleSeatErrorCode implements BaseCode {

	SCHEDULE_SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_SEAT_404_01", "존재하지 않는 일정 좌석입니다."),
	NOT_RESERVED_SEAT(HttpStatus.CONFLICT, "SCHEDULE_SEAT_409_01", "RESERVED 상태의 좌석이 아닙니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
