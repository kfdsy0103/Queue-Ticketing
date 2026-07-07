package ticketing.global.apiPayload.code;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GeneralSuccessCode implements BaseCode {

    // 200
    OK(HttpStatus.OK, "COMMON_200_01", "요청이 성공적으로 처리되었습니다."),

    // 201
    CREATED(HttpStatus.CREATED, "COMMON_201_01", "리소스가 성공적으로 생성되었습니다."),

    // 204
    NO_CONTENT(HttpStatus.NO_CONTENT, "COMMON_204_01", "요청이 성공적으로 처리되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}