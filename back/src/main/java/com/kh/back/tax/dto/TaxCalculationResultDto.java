package com.kh.back.tax.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

// 결과 페이지에서 함께 사용할 계산 결과와 월별 목록을 전달한다.
@Getter
@AllArgsConstructor
public class TaxCalculationResultDto {
    private final TaxBreakdownDto breakdown;             // 총액·총세금·산출 근거
    private final List<MonthlyAmountDto> monthlyIncomes;  // 월별 실제·예상 수입
    private final List<MonthlyAmountDto> monthlyExpenses; // 월별 실제·예상 지출
    private final Boolean businessRegistered;            // 등록 여부에 따른 안내용
}
