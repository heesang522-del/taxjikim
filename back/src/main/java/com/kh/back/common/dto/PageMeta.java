package com.kh.back.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 목록 응답의 페이징 정보. 페이지 번호는 1부터 시작한다.
 */
@Getter
@AllArgsConstructor
public class PageMeta {

    private final int page;
    private final int size;
    private final int total;
    private final int totalPages;

    public static PageMeta of(int page, int size, int total) {
        int totalPages = (total + size - 1) / size; // 올림 나눗셈
        return new PageMeta(page, size, total, totalPages);
    }
}