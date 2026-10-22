package com.kh.back.taxupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfParseResponseDto {

    private String fileName;

    private int totalPages;

    private int extractedCount;

    private int parsedCount;

    private int failedCount;

    private List<PageResult> pageResults;

    private List<TaxTransactionDto> transactions;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageResult {

        private int pageNumber;   // 페이지숫자

        private String method;    // 메소드

        private int extractedCount;   // 추출된 횟수

        private int parsedCount;   // 파싱된 횟수
    }
}