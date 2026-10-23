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
     * PDF 분석 응답에만 사용하는 필드다.
     * tax_transactions에는 저장하지 않는다.
     */
    private BigDecimal taxDeducted;
    private BigDecimal actualAmount;

    private Integer pageNumber;
    private String rawText;

    private Boolean parseSuccess;
    private Boolean excluded;
    private String failureReason;

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

        private Long userId;  // 사용자 id
        private boolean admin;  // 관리자
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