package com.kh.back.tax.dto;

/** 거래를 합산한 뒤 계산 서비스에 전달하는 금액 묶음. 금액 단위는 원이다. */
public record TaxCalculationInputDto(
        long annualGrossIncome, // 실제·예상 세전 수입의 연간 합계.
        long annualBusinessExpense, // 기부금을 제외한 연간 업무 경비.
        long recognizedDonation, // 한도 검토를 거친 기부금 인정액.
        long incomeDeduction // 본인 기본공제를 포함한 소득공제 총액.
) {
    public TaxCalculationInputDto {
        requireNonNegative("세전 수입", annualGrossIncome);
        requireNonNegative("업무 경비", annualBusinessExpense);
        requireNonNegative("기부금 인정액", recognizedDonation);
        requireNonNegative("소득공제 총액", incomeDeduction);
    }

    private static void requireNonNegative(String name, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException(name + "은 음수일 수 없습니다.");
        }
    }
}
