package com.kh.back.TaxUpload.dto;

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

        private int pageNumber;

        private String method;

        private int extractedCount;

        private int parsedCount;
    }
}