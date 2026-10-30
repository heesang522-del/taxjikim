package com.kh.back.tax.dto;

import lombok.Getter;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

// 산출 근거에 표시할 단계별 계산 결과를 담고, 이후 공제·세금 계산값도 여기에 추가한다.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxBreakdownDto {

    private static final BigDecimal GENERAL_DONATION_RATE = new BigDecimal("0.30");
    private static final BigDecimal BASIC_DEDUCTION_PER_PERSON = new BigDecimal("1500000");
    private static final BigDecimal STANDARD_TAX_CREDIT = new BigDecimal("70000");
    private static final BigDecimal LOCAL_TAX_RATE = new BigDecimal("0.10");

    private Integer taxYear;                    // 계산할 귀속연도
    private Integer dependentCount;             // 본인 제외 기본공제 대상 가족 수
    private BigDecimal totalIncome;             // 실제·예상 세전 수입 합계
    private BigDecimal totalExpense;            // 실제·예상 지출 합계
    private BigDecimal incomeBeforeDonation;    // 기부금 반영 전 사업소득 (적자도 유지)
    private BigDecimal donationBaseAmount;      // 기부금 한도용 기준금액 (최소 0원)
    private BigDecimal specialDonationAmount;   // 특례기부금 입력액
    private BigDecimal specialDonationAllowed;  // 한도 내 특례기부금 인정액
    private BigDecimal generalDonationAmount;   // 일반기부금 입력액 (종교단체 제외)
    private BigDecimal generalDonationLimit;    // 특례 인정액 차감 후 일반기부금 한도
    private BigDecimal generalDonationAllowed;  // 한도 내 일반기부금 인정액
    private BigDecimal incomeAfterDonation;     // 기부금 반영 후 사업소득
    private BigDecimal basicDeductionAmount;    // 본인과 가족의 기본공제액
    private BigDecimal taxBase;                 // 세율을 적용할 과세표준
    private BigDecimal taxRate;                 // 적용 세율 (15%는 0.15)
    private BigDecimal progressiveDeduction;    // 해당 세율 구간의 누진공제액
    private BigDecimal assessedTax;             // 세액공제 전 산출세액
    private BigDecimal standardTaxCredit;       // 산출세액 안에서 적용한 표준세액공제액
    private BigDecimal incomeTax;               // 표준세액공제 후 소득세
    private BigDecimal localIncomeTax;          // 개인지방소득세 예상액
    private BigDecimal totalTax;                // 소득세와 개인지방소득세 합계

    // 조회한 입력값을 복사해 계산을 시작할 산출 근거 객체를 만든다.
    public static TaxBreakdownDto from(TaxCalculationInputDto input) {
        return TaxBreakdownDto.builder()
                .taxYear(input.getTaxYear())
                .dependentCount(input.getDependentCount())
                .totalIncome(input.getTotalIncome())
                .totalExpense(input.getTotalExpense())
                .specialDonationAmount(input.getSpecialDonationAmount())
                .generalDonationAmount(input.getGeneralDonationAmount())
                .build();
    }

    // ① 사업소득은 적자도 유지하되 기부금 한도용 금액은 최소 0원으로 만든다.
    public TaxBreakdownDto calculateIncomeBeforeDonation() {
        incomeBeforeDonation = totalIncome.subtract(totalExpense);
        donationBaseAmount = incomeBeforeDonation.max(BigDecimal.ZERO);
        return this;
    }

    // ② 특례기부금(국가·지자체 기부 등)의 입력액을 기준금액 안에서 인정한다.
    public TaxBreakdownDto calculateSpecialDonation() {
        specialDonationAllowed = specialDonationAmount.min(donationBaseAmount);
        return this;
    }

    // ③ 일반기부금(복지·문화단체 등, 종교 제외)의 한도와 인정액을 계산한다.
    public TaxBreakdownDto calculateGeneralDonation() {
        generalDonationLimit = donationBaseAmount.subtract(specialDonationAllowed)
                .max(BigDecimal.ZERO).multiply(GENERAL_DONATION_RATE);
        generalDonationAllowed = generalDonationAmount.min(generalDonationLimit);
        return this;
    }

    // ④ 두 기부금의 인정액만 한 번씩 차감해 사업소득을 계산한다.
    public TaxBreakdownDto calculateIncomeAfterDonation() {
        incomeAfterDonation = incomeBeforeDonation
                .subtract(specialDonationAllowed).subtract(generalDonationAllowed);
        return this;
    }

    // ⑤ 본인 1명과 입력받은 공제 대상 가족 수에 기본공제를 적용한다.
    public TaxBreakdownDto calculateBasicDeduction() {
        basicDeductionAmount = BigDecimal.valueOf((long) dependentCount + 1)
                .multiply(BASIC_DEDUCTION_PER_PERSON);
        return this;
    }

    // ⑥ 기본공제를 차감한 과세표준은 최소 0원으로 하고 1원 미만은 버린다.
    public TaxBreakdownDto calculateTaxBase() {
        taxBase = incomeAfterDonation.subtract(basicDeductionAmount)
                .max(BigDecimal.ZERO).setScale(0, RoundingMode.DOWN);
        return this;
    }

    // ⑦ 2026년 기본세율 구간과 누진공제를 적용해 산출세액을 계산한다.
    public TaxBreakdownDto calculateAssessedTax() {
        if (taxBase.compareTo(BigDecimal.valueOf(14000000)) <= 0) {
            applyTaxBracket("0.06", "0");
        } else if (taxBase.compareTo(BigDecimal.valueOf(50000000)) <= 0) {
            applyTaxBracket("0.15", "1260000");
        } else if (taxBase.compareTo(BigDecimal.valueOf(88000000)) <= 0) {
            applyTaxBracket("0.24", "5760000");
        } else if (taxBase.compareTo(BigDecimal.valueOf(150000000)) <= 0) {
            applyTaxBracket("0.35", "15440000");
        } else if (taxBase.compareTo(BigDecimal.valueOf(300000000)) <= 0) {
            applyTaxBracket("0.38", "19940000");
        } else if (taxBase.compareTo(BigDecimal.valueOf(500000000)) <= 0) {
            applyTaxBracket("0.40", "25940000");
        } else if (taxBase.compareTo(BigDecimal.valueOf(1000000000)) <= 0) {
            applyTaxBracket("0.42", "35940000");
        } else {
            applyTaxBracket("0.45", "65940000");
        }
        assessedTax = taxBase.multiply(taxRate).subtract(progressiveDeduction)
                .max(BigDecimal.ZERO).setScale(0, RoundingMode.DOWN);
        return this;
    }

    // 선택한 세율과 누진공제액을 산출 근거에 함께 남긴다.
    private void applyTaxBracket(String rate, String deduction) {
        taxRate = new BigDecimal(rate);
        progressiveDeduction = new BigDecimal(deduction);
    }

    // ⑧ 일반 기준 표준세액공제 7만원을 세금 한도 내 적용하고 소득세 끝수를 처리한다.
    public TaxBreakdownDto calculateIncomeTax() {
        standardTaxCredit = assessedTax.min(STANDARD_TAX_CREDIT);
        incomeTax = truncateToTenWon(assessedTax.subtract(standardTaxCredit));
        return this;
    }

    // ⑨ 합의한 예상 계산 방식으로 소득세의 10%를 지방소득세로 반영한다.
    public TaxBreakdownDto calculateLocalIncomeTax() {
        localIncomeTax = truncateToTenWon(incomeTax.multiply(LOCAL_TAX_RATE));
        return this;
    }

    // ⑩ 원천징수액을 차감하지 않은 두 세금의 합계를 구한다.
    public TaxBreakdownDto calculateTotalTax() {
        totalTax = incomeTax.add(localIncomeTax);
        return this;
    }

    // 최종 세액은 반올림하지 않고 10원 미만 끝수를 버린다.
    private BigDecimal truncateToTenWon(BigDecimal amount) {
        return amount.divide(BigDecimal.TEN, 0, RoundingMode.DOWN).multiply(BigDecimal.TEN);
    }
}
