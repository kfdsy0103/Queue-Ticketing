package ticketing.domain.seatgrade.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.global.apiPayload.code.BaseCode;

@Getter
@RequiredArgsConstructor
public enum SeatGradeErrorCode implements BaseCode {

	SEAT_GRADE_NOT_FOUND(HttpStatus.NOT_FOUND, "SEAT_GRADE_404_01", "존재하지 않는 좌석 등급입니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
