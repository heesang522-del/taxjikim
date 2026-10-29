package com.kh.back.tax.service;

import com.kh.back.tax.dto.TaxDto;
import com.kh.back.tax.dto.MonthlyExpenseDto;
import com.kh.back.tax.mapper.TaxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

// 세금 분석 결과 조회와 계산을 처리한다.
@Service
@RequiredArgsConstructor
public class TaxServiceImpl implements TaxService {

    private final TaxMapper taxMapper;

    // 월별 지출을 조회하고 올해 자료이면 남은 달에 평균 지출을 채운다.
    @Override
    @Transactional(readOnly = true)
    public List<MonthlyExpenseDto> getMonthlyExpenses(Long taxInputId) {
        List<MonthlyExpenseDto> expenses = new ArrayList<>(taxMapper.findMonthlyExpenses(taxInputId));
        LocalDate lastDate = taxMapper.findLastTransactionDate(taxInputId);
        int currentYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();

        // 다른 연도 자료이거나 12월까지 자료가 있으면 실제 집계만 반환한다.
        if (lastDate == null || lastDate.getYear() != currentYear || lastDate.getMonthValue() == 12) {
            return expenses;
        }

        LocalDate firstDate = taxMapper.findFirstTransactionDate(taxInputId);
        int startMonth = firstDate.getMonthValue();
        int lastMonth = lastDate.getMonthValue();
        int monthCount = lastMonth - startMonth + 1;

        BigDecimal totalExpense = BigDecimal.ZERO;
        for (MonthlyExpenseDto expense : expenses) {
            totalExpense = totalExpense.add(expense.getActualExpense());
        }

        // 거래가 없는 중간 달도 기간에 포함하며, 평균은 소수점 둘째 자리까지 반올림한다.
        BigDecimal averageExpense = totalExpense.divide(
                BigDecimal.valueOf(monthCount), 2, RoundingMode.HALF_UP);

        // 요청한 응답 형식을 유지하기 위해 예상 금액도 actualExpense에 담는다. DB에는 저장하지 않는다.
        for (int month = lastMonth + 1; month <= 12; month++) {
            MonthlyExpenseDto expense = new MonthlyExpenseDto();
            expense.setYear(currentYear);
            expense.setMonth(month);
            expense.setActualExpense(averageExpense);
            expenses.add(expense);
        }

        return expenses;
    }

    // 분석번호로 실제 총지출을 조회한다.
    @Override
    @Transactional(readOnly = true)
    public TaxDto getTotalExpense(Long taxInputId) {
        BigDecimal totalExpense = taxMapper.findTotalExpense(taxInputId);

        // 이번에 조회하지 않은 총수입은 null로 둔다.
        return new TaxDto(taxInputId, totalExpense, null);
    }

    // 분석번호로 세전 총수입을 조회한다.
    @Override
    @Transactional(readOnly = true)
    public TaxDto getTotalIncome(Long taxInputId) {
        BigDecimal totalIncome = taxMapper.findTotalIncome(taxInputId);

        // 이번에 조회하지 않은 총지출은 null로 둔다.
        return new TaxDto(taxInputId, null, totalIncome);
    }
}
