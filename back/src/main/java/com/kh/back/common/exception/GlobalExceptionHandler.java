package com.kh.back.common.exception;

import com.kh.back.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 Controller 에서 던져진 예외를 한곳에서 받아 실패 봉투로 응답한다.
 * 덕분에 Controller 마다 try-catch 를 쓰지 않아도 된다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Service 에서 직접 던진 예외 (예: 없는 종목코드 → 404)
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(ApiResponse.fail(e.getCode(), e.getMessage()));
    }

    // 없는 주소로 요청 (예: /api/v1/abc)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("NOT_FOUND", "요청한 주소가 없습니다."));
    }

    // 파라미터 타입이 맞지 않음 (예: ?page=abc)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail("BAD_REQUEST", e.getName() + " 값이 올바르지 않습니다."));
    }

    // 그 밖의 모든 예외 → 500. 원인은 서버 로그에만 남기고 응답에는 노출하지 않는다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleEtc(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("INTERNAL_ERROR", "서버 오류가 발생했습니다."));
    }
}