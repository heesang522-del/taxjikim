package com.kh.back.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import java.util.Map;

/**
 * Service 에서 "요청은 정상이지만 처리할 수 없다"는 상황을 알릴 때 던지는 예외.
 * GlobalExceptionHandler 가 받아서 상태 코드와 실패 봉투로 바꿔 응답한다.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fields;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.code = code;
        this.fields = fields;
    }
}
