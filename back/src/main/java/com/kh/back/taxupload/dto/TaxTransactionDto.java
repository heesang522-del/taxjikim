package com.kh.back.taxupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

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

    /*
     * PDF 분석 결과 전달용 필드다.
     * 아래 필드는 현재 tax_transactions에 저장하지 않는다.
     */
    private BigDecimal taxDeducted;
    private BigDecimal actualAmount;

    private Integer pageNumber;
    private String rawText;

    private Boolean parseSuccess;
    private Boolean excluded;
    private String failureReason;

    // 월별 자료는 실제 거래일을 생성하지 않고 월과 연도를 전달한다.
    private String transactionMonth;
    private Integer documentYear;

    // 상세·월별 집계 등 자료의 집계 단위를 전달한다.
    private String valueKind;

    // 실제 입출금액과 공제대상금액을 구분한다.
    private String amountBasis;

    // 사용자 확인이 필요한 분석 결과인지 전달한다.
    private Boolean needsReview;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxInputRequest {

        // 요청으로 전달돼도 식별자와 소유자는 서버에서 결정한다.
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
    public static class CategoryRequest {

        private String category;
    }

    /*
     * HTTP 요청 본문으로 받지 않는다.
     * Controller가 인증 정보와 서버 세션으로 생성한다.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessContext {

        private Long userId;
        private boolean admin;
        private Set<Long> guestInputIds;
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
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxResultResponse {

        private Long taxInputId;
        private Map<String, Object> resultDetail;
        private String calculatedAt;
    }
}