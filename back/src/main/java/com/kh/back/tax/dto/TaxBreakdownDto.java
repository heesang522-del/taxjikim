package com.kh.back.tax.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// 산출 근거에 표시할 단계별 계산 결과를 담고, 이후 공제·세금 계산값도 여기에 추가한다.
@Getter
@Setter
@NoArgsConstructor
public class TaxBreakdownDto {

    private BigDecimal incomeBeforeDonation;    // 기부금 반영 전 사업소득 (적자도 유지)
    private BigDecimal donationBaseAmount;      // 기부금 한도용 기준금액 (최소 0원)
    private BigDecimal specialDonationAmount;   // 특례기부금 입력액
    private BigDecimal specialDonationAllowed;  // 한도 내 특례기부금 인정액
    private BigDecimal generalDonationAmount;   // 일반기부금 입력액 (종교단체 제외)
    private BigDecimal generalDonationLimit;    // 특례 인정액 차감 후 일반기부금 한도
    private BigDecimal generalDonationAllowed;  // 한도 내 일반기부금 인정액
    private BigDecimal incomeAfterDonation;     // 기부금 반영 후 사업소득
}
