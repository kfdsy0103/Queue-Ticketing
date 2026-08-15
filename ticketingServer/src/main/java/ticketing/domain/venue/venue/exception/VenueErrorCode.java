package ticketing.domain.venue.venue.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.global.apiPayload.code.BaseCode;

@Getter
@RequiredArgsConstructor
public enum VenueErrorCode implements BaseCode {

	VENUE_NOT_FOUND(HttpStatus.NOT_FOUND, "VENUE_404_01", "존재하지 않는 공연장입니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
