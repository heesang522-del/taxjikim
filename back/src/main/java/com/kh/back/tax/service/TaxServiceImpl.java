package com.kh.back.tax.service;

import com.kh.back.tax.dto.TaxCalculationInputDto;
import com.kh.back.tax.dto.CategoryAmountDto;
import com.kh.back.tax.dto.CategoryRatioDto;
import com.kh.back.tax.dto.TaxBreakdownDto;
import com.kh.back.tax.dto.TaxCalculationResultDto;
import com.kh.back.tax.exception.TaxCalculationException;
import com.kh.back.common.exception.ApiException;
import com.kh.back.tax.dto.MonthlyAmountDto;
import com.kh.back.tax.dto.MonthlyTransactionDto;
import com.kh.back.tax.dto.TransactionPeriodDto;
import com.kh.back.tax.mapper.TaxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
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
@Slf4j
public class TaxServiceImpl implements TaxService {

    private final TaxMapper taxMapper;

    // 거래 기간과 월별 목록을 한 번씩 조회해 계산과 그래프에서 함께 사용한다.
    @Override
    @Transactional(readOnly = true)
    public TaxCalculationResultDto getCalculationResult(Long taxInputId) {
        // 1. 분석 기본정보와 공제 조건을 조회한다.
        TaxCalculationInputDto input = taxMapper.findCalculationInput(taxInputId);
        if (input == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TAX_INPUT_NOT_FOUND", "분석 정보를 찾을 수 없습니다.");
        }

        // 2. 첫·마지막 거래 날짜가 있고, 두 날짜의 연도가 같은지 확인한다.
        TransactionPeriodDto period = taxMapper.findTransactionPeriod(taxInputId);
        validateTransactionPeriod(period);

        // 3. 월별 거래를 한 번 조회하고 수입·지출 목록으로 나눈다.
        List<MonthlyTransactionDto> monthlyTransactions = taxMapper.findMonthlyTransactions(taxInputId);
        List<MonthlyAmountDto> incomes = new ArrayList<>();
        List<MonthlyAmountDto> expenses = new ArrayList<>();
        BigDecimal actualNetIncome = BigDecimal.ZERO;
        boolean hasMissingNetIncome = false;
        for (MonthlyTransactionDto transaction : monthlyTransactions) {
            MonthlyAmountDto amount = new MonthlyAmountDto();
            amount.setMonth(transaction.getMonth());
            amount.setAmount(transaction.getAmount());
            if ("INCOME".equals(transaction.getTransactionType())) {
                incomes.add(amount);
                // 실제 입금액만 합산하며, 금액 누락을 세금 차감으로 오인하지 않는다.
                if (transaction.getNetIncome() == null) {
                    hasMissingNetIncome = true;
                } else {
                    actualNetIncome = actualNetIncome.add(transaction.getNetIncome());
                }
            } else if ("EXPENSE".equals(transaction.getTransactionType())) {
                expenses.add(amount);
            }
        }

        // 4. 실제 합계를 보관하고, 올해 자료라면 남은 달에 월평균 예상 금액을 추가한다.
        BigDecimal actualIncome = sumMonthlyAmounts(incomes);
        BigDecimal actualExpense = sumMonthlyAmounts(expenses);
        // 국내·해외 구분 전의 차액이다. 미래 예상 수입은 포함하지 않는다.
        BigDecimal deductedAmount = null;
        if (hasMissingNetIncome) {
            actualNetIncome = null;
        } else {
            deductedAmount = actualIncome.subtract(actualNetIncome);
        }
        incomes = addPredictedMonths(incomes, period);
        expenses = addPredictedMonths(expenses, period);

        // 5. 실제·예상 목록을 합산해 세금 계산에 사용할 총수입·총지출을 채운다.
        input.setTotalIncome(sumMonthlyAmounts(incomes));
        input.setTotalExpense(sumMonthlyAmounts(expenses));

        // 6. 차감액 전액을 반영하는 단순 정산 예상이다. 외국납부세액 공제한도는 반영하지 않는다.
        TaxBreakdownDto breakdown = calculateBreakdown(input);
        String settlementType = null;
        BigDecimal settlementAmount = null;
        if (deductedAmount != null && deductedAmount.signum() >= 0) {
            BigDecimal balance = breakdown.getTotalTax().subtract(deductedAmount);
            if (balance.signum() > 0) {
                settlementType = "PAYMENT";
            } else if (balance.signum() < 0) {
                settlementType = "REFUND";
            } else {
                settlementType = "NONE";
            }
            settlementAmount = balance.abs();
        }

        // 7. 실제 거래만 사용해 수입과 지출의 카테고리 비율을 각각 계산한다.
        List<CategoryAmountDto> categories = taxMapper.findCategoryAmounts(taxInputId);
        return new TaxCalculationResultDto(breakdown, incomes, expenses,
                input.getBusinessRegistered(), actualIncome,
                input.getTotalIncome().subtract(actualIncome), actualExpense,
                input.getTotalExpense().subtract(actualExpense), actualNetIncome, deductedAmount,
                settlementType, settlementAmount,
                categoryRatios(categories, "INCOME"), categoryRatios(categories, "EXPENSE"));
    }

    // 금액이 있는 카테고리의 비율을 같은 거래 구분의 합계로 계산한다.
    private List<CategoryRatioDto> categoryRatios(List<CategoryAmountDto> categories, String transactionType) {
        List<CategoryAmountDto> amounts = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        // 같은 거래 구분에서 금액이 있는 카테고리만 모아 합계를 구한다.
        for (CategoryAmountDto category : categories) {
            if (transactionType.equals(category.transactionType()) && category.amount().signum() > 0) {
                amounts.add(category);
                total = total.add(category.amount());
            }
        }

        List<CategoryRatioDto> ratios = new ArrayList<>();
        for (CategoryAmountDto category : amounts) {
            BigDecimal percentage = category.amount().multiply(BigDecimal.valueOf(100))
                    .divide(total, 2, RoundingMode.HALF_UP);
            ratios.add(new CategoryRatioDto(category.category(), category.amount(), percentage));
        }
        return ratios;
    }

    // 준비된 입력값으로 산출 근거를 계산하며 DB 조회는 수행하지 않는다.
    private TaxBreakdownDto calculateBreakdown(TaxCalculationInputDto input) {
        try {
            return TaxBreakdownDto.from(input)
                    .calculateIncomeBeforeDonation()
                    .calculateSpecialDonation()
                    .calculateGeneralDonation()
                    .calculateIncomeAfterDonation()
                    .calculateBasicDeduction()
                    .calculateTaxBase()
                    .calculateAssessedTax()
                    .calculateIncomeTax()
                    .calculateLocalIncomeTax()
                    .calculateTotalTax();
        } catch (RuntimeException exception) {
            // 계산 단계의 실패만 변환하며, DB 조회 오류는 이 범위에 포함하지 않는다.
            log.error("내부 세금 계산 실패", exception);
            throw new TaxCalculationException(exception);
        }
    }

    // 올해 자료의 남은 달에 월평균 예상 금액을 추가한다.
    private List<MonthlyAmountDto> addPredictedMonths(
            List<MonthlyAmountDto> monthlyAmounts, TransactionPeriodDto period) {
        // 통합 조회에서 확인한 기간을 수입·지출 예상 계산에 함께 사용한다.
        // 조회한 원래 목록은 유지하고, 응답할 목록에만 예상 월을 추가한다.
        List<MonthlyAmountDto> amounts = new ArrayList<>(monthlyAmounts);
        LocalDate lastDate = period.getLastDate();
        int currentYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();

        // 다른 연도 자료이거나 12월까지 자료가 있으면 실제 집계만 반환한다.
        if (lastDate.getYear() != currentYear || lastDate.getMonthValue() == 12) {
            return amounts;
        }

        int startMonth = period.getFirstDate().getMonthValue();
        int lastMonth = lastDate.getMonthValue();
        int monthCount = lastMonth - startMonth + 1;
        BigDecimal totalAmount = sumMonthlyAmounts(amounts);

        // 거래가 없는 중간 달도 기간에 포함하며, 평균은 정수 금액으로 반올림한다.
        long averageAmount = totalAmount.divide(
                BigDecimal.valueOf(monthCount), 0, RoundingMode.HALF_UP).longValueExact();

        // 예상 금액은 표시용 목록에만 추가하고, DB에는 저장하지 않는다.
        for (int month = lastMonth + 1; month <= 12; month++) {
            MonthlyAmountDto amount = new MonthlyAmountDto();
            amount.setMonth(month);
            amount.setAmount(averageAmount);
            amount.setPredicted(true);
            amounts.add(amount);
        }
        return amounts;
    }

    // 월별 집계에서 서로 다른 연도가 같은 월로 섞이지 않도록 자료 기간을 확인한다.
    private void validateTransactionPeriod(TransactionPeriodDto period) {
        if (period == null || period.getFirstDate() == null || period.getLastDate() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TAX_TRANSACTION_DATE_REQUIRED",
                    "계산할 거래 날짜가 없습니다.");
        }
        if (period.getFirstDate().getYear() != period.getLastDate().getYear()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TAX_TRANSACTION_YEAR_MISMATCH",
                    "여러 연도의 거래가 포함되어 있습니다. 같은 연도의 거래로 분석해주세요.");
        }
    }

    // 월별 목록의 금액을 모두 합산한다.
    private BigDecimal sumMonthlyAmounts(List<MonthlyAmountDto> monthlyAmounts) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (MonthlyAmountDto amount : monthlyAmounts) {
            totalAmount = totalAmount.add(BigDecimal.valueOf(amount.getAmount()));
        }
        return totalAmount;
    }

}
