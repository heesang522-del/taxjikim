package com.kh.back.tax.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// 총세금 계산에 사용할 합계 금액과 DB 입력 조건을 모아 전달한다.
@Getter
@Setter
@NoArgsConstructor
public class TaxCalculationInputDto {

    private Integer taxYear;                    // 소득의 귀속연도
    private Integer dependentCount;             // 본인 제외 기본공제 대상 가족 수
    private BigDecimal specialDonationAmount;   // 특례기부금 입력액 (한도 적용 전)
    private BigDecimal generalDonationAmount;   // 일반기부금 입력액 (종교단체 제외, 한도 적용 전)
    private BigDecimal totalIncome;             // 실제·예상 세전 수입 합계
    private BigDecimal totalExpense;            // 실제·예상 지출 합계 (기부금 별도)
}
