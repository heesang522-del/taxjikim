package com.kh.back.tax.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.math.BigDecimal;

// 결과 페이지에서 함께 사용할 계산 결과와 월별 목록을 전달한다.
@Getter
@AllArgsConstructor
public class TaxCalculationResultDto {
    private final TaxBreakdownDto breakdown;             // 총액·총세금·산출 근거
    private final List<MonthlyAmountDto> monthlyIncomes;  // 월별 실제·예상 수입
    private final List<MonthlyAmountDto> monthlyExpenses; // 월별 실제·예상 지출
    private final Boolean businessRegistered;            // 등록 여부에 따른 안내용
    private final BigDecimal actualIncome;               // 실제 세전 수입 합계
    private final BigDecimal predictedIncome;            // 남은 달 예상 수입 합계
    private final BigDecimal actualExpense;              // 실제 지출 합계
    private final BigDecimal predictedExpense;           // 남은 달 예상 지출 합계
    private final BigDecimal actualNetIncome;            // 실제 세후 수입 합계 (누락 시 null)
    private final BigDecimal deductedAmount;             // 실제 세전 - 세후 차액, 국내 기납부세액으로 단정하지 않음
    private final String settlementType;                 // PAYMENT / REFUND / NONE, 계산 불가 시 null
    private final BigDecimal settlementAmount;           // 차감액 전액 반영 가정의 정산 예상액 (양수로 표시)
    private final List<CategoryAmountDto> incomeCategories;  // 실제 세전 수입의 카테고리 비율
    private final List<CategoryAmountDto> expenseCategories; // 실제 지출의 카테고리 비율
}
