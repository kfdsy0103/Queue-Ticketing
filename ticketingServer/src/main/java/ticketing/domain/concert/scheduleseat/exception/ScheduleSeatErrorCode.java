package ticketing.domain.concert.scheduleseat.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.global.apiPayload.code.BaseCode;

@Getter
@RequiredArgsConstructor
public enum ScheduleSeatErrorCode implements BaseCode {

	SCHEDULE_SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_SEAT_404_01", "존재하지 않는 일정 좌석입니다."),
	NOT_AVAILABLE_SEAT(HttpStatus.CONFLICT, "SCHEDULE_SEAT_409_01", "AVAILABLE 상태의 좌석이 아닙니다."),
	ALREADY_OCCUPIED(HttpStatus.CONFLICT, "SCHEDULE_SEAT_409_02", "다른 사용자가 이미 점유한 좌석이 포함되어 있습니다."),
	NOT_SOLD_SEAT(HttpStatus.CONFLICT, "SCHEDULE_SEAT_409_03", "SOLD 상태의 좌석이 아닙니다."),
	NOT_OCCUPIED_BY_USER(HttpStatus.FORBIDDEN, "SCHEDULE_SEAT_403_01", "본인이 점유한 좌석이 아니거나 점유 시간이 만료되었습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
