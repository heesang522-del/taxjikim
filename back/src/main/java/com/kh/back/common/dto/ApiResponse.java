package com.kh.back.common.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 모든 API 가 같은 모양으로 응답하기 위한 공통 봉투.
 * { "success": true, "data": ..., "meta": ..., "error": null }
 * React 는 이 모양을 전제로 res.data.data 를 꺼내 쓴다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final PageMeta meta;   // 목록 조회일 때만 채운다
    private final ErrorInfo error; // 실패일 때만 채운다

    // 단건 조회 성공
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    // 목록 조회 성공 (페이징 정보 포함)
    public static <T> ApiResponse<T> ok(T data, PageMeta meta) {
        return new ApiResponse<>(true, data, meta, null);
    }

    // 실패
    public static ApiResponse<Void> fail(String code, String message) {
        return new ApiResponse<>(false, null, null, new ErrorInfo(code, message));
    }

    @Getter
    @AllArgsConstructor
    public static class ErrorInfo {
        private final String code;    // 예: COMPANY_NOT_FOUND
        private final String message; // 사람이 읽는 설명
    }
}