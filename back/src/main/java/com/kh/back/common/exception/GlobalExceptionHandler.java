package com.kh.back.common.exception;

import com.kh.back.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.validation.BindException;
import org.springframework.security.core.AuthenticationException;
import com.kh.back.auth.security.LoginFailureException;
import com.kh.back.auth.dto.LoginAttemptDto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailException;
import java.util.LinkedHashMap;
import java.util.Map;

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
        return ResponseEntity.status(e.getStatus()).body(ApiResponse.fail(e.getCode(), e.getMessage(), e.getFields()));
    }

    // 없는 주소로 요청 (예: /api/v1/abc)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("NOT_FOUND", "요청한 주소가 없습니다."));
    }

    // 파라미터 타입이 맞지 않음 (예: ?page=abc)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION_ERROR", "입력값을 확인해주세요.", Map.of(e.getName(), "값의 형식이 올바르지 않습니다.")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION_ERROR", "입력값을 확인해주세요.", Map.of(e.getParameterName(), "필수 입력 항목입니다.")));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Void>> handleUnreadableInput(Exception e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION_ERROR", "요청 데이터의 형식을 확인해주세요."));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBinding(BindException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), "값의 형식이 올바르지 않습니다."));
        return ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION_ERROR", "입력값을 확인해주세요.", fields));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResponse.fail("METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(ApiResponse.fail("UNSUPPORTED_MEDIA_TYPE", "요청 데이터 형식을 확인해주세요."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException e) {
        // 예외에서는 실패 사유와 기록만 받고, 사용자 안내 문구는 여기서 만든다.
        String code;
        LoginAttemptDto details = null;
        if (e instanceof LoginFailureException failure) {
            code = failure.getCode();
            details = failure.getDetails();
        } else {
            code = e.getMessage() == null ? "AUTHENTICATION_FAILED" : e.getMessage();
            if (!java.util.Set.of("DORMANT", "LOCKED", "BANNED", "SESSION_REPLACED").contains(code)) {
                code = "AUTHENTICATION_FAILED";
            }
        }
        String message = switch (code) {
            case "DORMANT" -> "휴면 계정입니다. 재활성화가 필요합니다.";
            case "LOCKED" -> "로그인 실패로 계정이 잠겼습니다. 관리자에게 문의하세요.";
            case "BANNED" -> "이용이 정지된 계정입니다.";
            case "LOGIN_FAILED" -> "아이디 또는 비밀번호를 확인해주세요."
                    + (details != null ? " 첫 잠금까지 " + details.remainingAttempts() + "회 남았습니다." : "");
            case "LOGIN_TEMPORARILY_BLOCKED" -> "로그인이 일시 제한되었습니다. 남은 시간 후 다시 시도해주세요."
                    + (details != null && details.failedAttempts() == 9
                    ? " 다음 실패 시 계정이 잠기며 관리자 해제가 필요합니다." : "");
            case "SESSION_REPLACED" -> "현재 로그인이 종료되었습니다. 다시 로그인해주세요.";
            default -> "인증 정보가 올바르지 않거나 만료되었습니다.";
        };
        HttpStatus status = "LOGIN_TEMPORARILY_BLOCKED".equals(code)
                ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status).body(ApiResponse.fail(code, message, null, details));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataConflict(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail("DATA_CONFLICT", "중복 정보 또는 다른 데이터와의 연결을 확인해주세요."));
    }

    @ExceptionHandler(MailException.class)
    public ResponseEntity<ApiResponse<Void>> handleMail(MailException e) {
        log.error("메일 발송 실패", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.fail("MAIL_UNAVAILABLE", "메일 발송을 완료하지 못했습니다. 잠시 후 다시 시도해주세요."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.fail("UPLOAD_SIZE_EXCEEDED", "업로드 요청의 용량 제한을 초과했습니다. 파일을 청크로 나누어 전송해주세요."));
    }

    // 그 밖의 모든 예외 → 500. 원인은 서버 로그에만 남기고 응답에는 노출하지 않는다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleEtc(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("INTERNAL_ERROR", "서버 오류가 발생했습니다."));
    }
}
