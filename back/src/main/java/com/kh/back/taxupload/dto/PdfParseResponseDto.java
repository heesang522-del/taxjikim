package com.kh.back.taxupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

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

    // 표 원문과 좌표 등 분석 정보를 전달한다.
    private List<Map<String, Object>> tables;

    // DB 저장이 완료된 경우에만 true로 반환한다.
    private boolean persisted;

    // 연도별로 생성된 원본 분석번호를 전달한다.
    private Map<Integer, Long> taxInputIdsByYear;

    // 실제 DB에 저장한 거래 수
    private int savedCount;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageResult {

        private int pageNumber;
        private String method;

        private int extractedCount;
        private int parsedCount;
    }
}