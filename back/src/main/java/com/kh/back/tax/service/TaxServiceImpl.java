package com.kh.back.tax.service;

import com.kh.back.tax.dto.TaxDto;
import com.kh.back.tax.dto.MonthlyAmountDto;
import com.kh.back.tax.dto.TransactionPeriodDto;
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

    // 월별 세전 수입을 조회하고 올해 자료이면 남은 달에 평균 수입을 채운다.
    @Override
    @Transactional(readOnly = true)
    public List<MonthlyAmountDto> getMonthlyIncomes(Long taxInputId) {
        List<MonthlyAmountDto> incomes = new ArrayList<>(taxMapper.findMonthlyIncomes(taxInputId));
        TransactionPeriodDto period = taxMapper.findTransactionPeriod(taxInputId);
        LocalDate lastDate = period.getLastDate();
        int currentYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();

        // 날짜 자료가 없거나 다른 연도·12월 자료이면 실제 집계만 반환한다.
        if (lastDate == null || lastDate.getYear() != currentYear || lastDate.getMonthValue() == 12) {
            return incomes;
        }

        LocalDate firstDate = period.getFirstDate();
        int startMonth = firstDate.getMonthValue();
        int lastMonth = lastDate.getMonthValue();
        int monthCount = lastMonth - startMonth + 1;

        BigDecimal totalIncome = BigDecimal.ZERO;
        for (MonthlyAmountDto income : incomes) {
            totalIncome = totalIncome.add(BigDecimal.valueOf(income.getAmount()));
        }

        // 수입이 없는 중간 달도 기간에 포함하며, 평균은 정수 금액으로 반올림한다.
        long averageIncome = totalIncome.divide(
                BigDecimal.valueOf(monthCount), 0, RoundingMode.HALF_UP).longValueExact();

        // 예상 금액도 amount에 담아 응답하며, DB에는 저장하지 않는다.
        for (int month = lastMonth + 1; month <= 12; month++) {
            MonthlyAmountDto income = new MonthlyAmountDto();
            income.setMonth(month);
            income.setAmount(averageIncome);
            income.setPredicted(true);
            incomes.add(income);
        }

        return incomes;
    }

    // 월별 지출을 조회하고 올해 자료이면 남은 달에 평균 지출을 채운다.
    @Override
    @Transactional(readOnly = true)
    public List<MonthlyAmountDto> getMonthlyExpenses(Long taxInputId) {
        List<MonthlyAmountDto> expenses = new ArrayList<>(taxMapper.findMonthlyExpenses(taxInputId));
        TransactionPeriodDto period = taxMapper.findTransactionPeriod(taxInputId);
        LocalDate lastDate = period.getLastDate();
        int currentYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();

        // 다른 연도 자료이거나 12월까지 자료가 있으면 실제 집계만 반환한다.
        if (lastDate.getYear() != currentYear || lastDate.getMonthValue() == 12) {
            return expenses;
        }

        LocalDate firstDate = period.getFirstDate();
        int startMonth = firstDate.getMonthValue();
        int lastMonth = lastDate.getMonthValue();
        int monthCount = lastMonth - startMonth + 1;

        BigDecimal totalExpense = BigDecimal.ZERO;
        for (MonthlyAmountDto expense : expenses) {
            totalExpense = totalExpense.add(BigDecimal.valueOf(expense.getAmount()));
        }

        // 거래가 없는 중간 달도 기간에 포함하며, 평균은 정수 금액으로 반올림한다.
        long averageExpense = totalExpense.divide(
                BigDecimal.valueOf(monthCount), 0, RoundingMode.HALF_UP).longValueExact();

        // 요청한 응답 형식을 유지하기 위해 예상 금액도 amount에 담는다. DB에는 저장하지 않는다.
        for (int month = lastMonth + 1; month <= 12; month++) {
            MonthlyAmountDto expense = new MonthlyAmountDto();
            expense.setMonth(month);
            expense.setAmount(averageExpense);
            expense.setPredicted(true);
            expenses.add(expense);
        }

        return expenses;
    }

    // 분석번호로 세전 총수입을 조회한다.
    @Override
    @Transactional(readOnly = true)
    public TaxDto getTotalIncome(Long taxInputId) {
        BigDecimal totalIncome = taxMapper.findTotalIncome(taxInputId);

        return new TaxDto(taxInputId, totalIncome);
    }

    // 분석번호로 실제 총지출을 조회한다.
    @Override
    @Transactional(readOnly = true)
    public TaxDto getTotalExpense(Long taxInputId) {
        BigDecimal totalExpense = taxMapper.findTotalExpense(taxInputId);

        return new TaxDto(taxInputId, totalExpense);
    }
}
