package com.kh.back.taxupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxTransactionDto {

    private Long transactionId;
    private Long taxInputId;

    private String transactionType;
    private LocalDate transactionDate;
    private String description;
    private String category;

    private BigDecimal preTaxIncome;
    private BigDecimal deductedTaxRate;
    private BigDecimal amount;

    // PDF 분석 결과 전달용이며 거래 테이블에는 저장하지 않는다.
    private BigDecimal taxDeducted;
    private BigDecimal actualAmount;

    private Integer pageNumber;
    private String rawText;

    private Boolean parseSuccess;
    private Boolean excluded;
    private String failureReason;

    private String transactionMonth;
    private Integer documentYear;

    private String valueKind;
    private String amountBasis;
    private Boolean needsReview;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxInputRequest {

        // 식별자와 소유자는 서비스에서 결정한다.
        private Long taxInputId;
        private Long userId;

        private Integer taxYear;

        private Boolean businessRegistered;
        private Integer age;
        private Boolean startupReliefApplied;
        private String businessRegion;

        private Integer dependentCount;
        private BigDecimal donationAmount;
        private String saveStatus;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxResultRow {

        private Long taxInputId;
        private String resultDetail;
        private LocalDateTime calculatedAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxServiceRequest {

        private String target;

        private Long taxInputId;
        private Long transactionId;
        private Integer taxYear;

        private TaxInputRequest input;
        private String category;

        // 최초 저장할 신규 거래 목록
        private List<TaxTransactionDto> transactions;
    }
}