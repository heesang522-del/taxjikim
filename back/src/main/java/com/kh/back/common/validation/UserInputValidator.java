package com.kh.back.common.validation;

import java.util.regex.Pattern;

public final class UserInputValidator {
    private static final Pattern LOGIN_ID = Pattern.compile("^[a-zA-Z0-9_]{4,20}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern HANGUL = Pattern.compile("\\p{IsHangul}");

    private UserInputValidator() {
    }

    public static String normalizeLoginId(String value) {
        InputValidator.requireText("loginId", value);
        String loginId = value.trim();
        if (!LOGIN_ID.matcher(loginId).matches()) {
            InputValidator.reject("loginId", "아이디는 영문·숫자·_ 4~20자로 입력하세요.");
        }
        return loginId;
    }

    public static String normalizeEmail(String value) {
        InputValidator.requireText("email", value);
        String email = value.trim();
        InputValidator.requireMaxLength("email", email, 255);
        if (HANGUL.matcher(email).find()) {
            InputValidator.reject("email", "이메일에는 한글을 사용할 수 없습니다.");
        }
        if (!EMAIL.matcher(email).matches()) {
            InputValidator.reject("email", "이메일 형식을 확인하세요.");
        }
        return email;
    }
}
