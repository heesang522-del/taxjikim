package com.kh.back.common.validation;

public final class PasswordValidator {
    private PasswordValidator() {
    }

    // 새 비밀번호를 정하는 모든 기능에 동일한 조건을 적용한다. 기존 비밀번호 대조에는 사용하지 않는다.
    public static void validate(String field, String password) {
        InputValidator.requireText(field, password);
        if (password.matches("(?sU).*\\s.*")) {
            InputValidator.reject(field, "비밀번호에는 공백을 포함할 수 없습니다.");
        }
        if (password.length() < 8 || password.length() > 20) {
            InputValidator.reject(field, "비밀번호는 8~20자로 입력해주세요.");
        }
        if (!password.matches("(?s).*[a-zA-Z].*") || !password.matches("(?s).*[0-9].*")
                || !password.matches("(?s).*[!@#$%^&*].*")) {
            InputValidator.reject(field, "영문, 숫자, 특수문자(!@#$%^&*)를 모두 포함해주세요.");
        }
    }
}
