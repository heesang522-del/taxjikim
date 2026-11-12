package com.kh.back.common.validation;

import com.kh.back.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import java.util.Map;

public final class InputValidator {
    private InputValidator() {
    }

    // 업무별 입력 조건은 호출부에서 정하고, 오류 형식은 여기서 통일한다.
    public static void requireText(String field, String value) {
        if (!StringUtils.hasText(value)) {
            reject(field, "필수 입력 항목입니다.");
        }
    }

    public static void requireMaxLength(String field, String value, int maxLength) {
        if (value != null && value.codePointCount(0, value.length()) > maxLength) {
            reject(field, maxLength + "자 이하로 입력해주세요.");
        }
    }

    public static void reject(String field, String message) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값을 확인해주세요.", Map.of(field, message));
    }
}
