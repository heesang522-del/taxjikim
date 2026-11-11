package com.kh.back.taxupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

        private Integer dependentCount;
        private BigDecimal generalDonationAmount; // 종교단체 외 일반기부금 입력액
        private BigDecimal specialDonationAmount; // 영수증상 특례기부금 입력액
        private String saveStatus;
    }

    /*
     * 서비스에서 로그인 정보와 검증된 비회원 토큰으로 생성한다.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessContext {

        private Long userId;
        private boolean admin;
        private Long guestTaxInputId;
    }

    /*
     * 통합 서비스 호출용 요청이다.
     * Controller가 작업 대상과 필요한 값을 builder로 구성한다.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxServiceRequest {

        // INPUT / SAVED_INPUTS / TRANSACTIONS / RESULT / CATEGORY / TRANSACTION
        private String target;

        private Long taxInputId;
        private String guestAccessToken; // 기존 비회원 분석 접근에 사용하는 토큰
        private Long transactionId;
        private java.util.List<Long> transactionIds;
        private Integer taxYear;

        private TaxInputRequest input;
        private List<TaxCategoryChange> changes;
        // 최초 저장할 신규 거래 목록
        private Integer expectedTransactionCount;
        private List<TaxTransactionDto> transactions;
    }
}
