package com.kh.back.tax.service;

import com.kh.back.tax.dto.TaxDto;
import com.kh.back.tax.dto.MonthlyAmountDto;

import java.util.List;

// 세금 분석 결과 처리 기능을 선언한다.
public interface TaxService {

    // 분석번호에 해당하는 월별 실제·예상 세전 수입 목록을 반환한다.
    List<MonthlyAmountDto> getMonthlyIncomes(Long taxInputId);

    // 분석번호에 해당하는 월별 실제 지출 목록을 반환한다.
    List<MonthlyAmountDto> getMonthlyExpenses(Long taxInputId);

    // 실제·예상 금액을 포함한 세전 총수입을 반환한다.
    TaxDto getTotalIncome(Long taxInputId);

    // 분석번호에 해당하는 실제 총지출을 반환한다.
    TaxDto getTotalExpense(Long taxInputId);
}
