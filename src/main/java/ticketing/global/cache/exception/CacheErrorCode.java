package ticketing.global.cache.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.global.apiPayload.code.BaseCode;

@Getter
@RequiredArgsConstructor
public enum CacheErrorCode implements BaseCode {

	CACHE_MANAGER_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "CACHE_500_01", "등록되지 않은 캐시 계층입니다."),
	LOCAL_CACHE_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "CACHE_500_02", "로컬 캐시에 등록되지 않은 캐시명입니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
