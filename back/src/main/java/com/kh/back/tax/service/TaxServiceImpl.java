package com.kh.back.tax.service;

import com.kh.back.tax.dto.TaxDto;
import com.kh.back.tax.dto.TaxCalculationInputDto;
import com.kh.back.tax.dto.DonationCalculationDto;
import com.kh.back.common.exception.ApiException;
import com.kh.back.tax.dto.MonthlyAmountDto;
import com.kh.back.tax.dto.TransactionPeriodDto;
import com.kh.back.tax.mapper.TaxMapper;
import lombok.RequiredArgsConstructor;
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
public class TaxServiceImpl implements TaxService {

    private final TaxMapper taxMapper;

    // 조회한 입력값으로 기부금 한도와 반영 후 사업소득을 계산한다.
    private DonationCalculationDto calculateDonations(TaxCalculationInputDto input) {
        // ① 적자 금액은 보존하되 기부금 인정 한도는 0원 아래로 내려가지 않게 한다.
        BigDecimal incomeBeforeDonation = input.getTotalIncome().subtract(input.getTotalExpense());
        BigDecimal donationBaseAmount = incomeBeforeDonation.max(BigDecimal.ZERO);

        // ② 개인사업자의 특례기부금은 기준금액 안에서 입력액만큼 인정한다.
        BigDecimal specialDonationAllowed = input.getSpecialDonationAmount().min(donationBaseAmount);

        // ③ 특례 인정액을 먼저 차감하고 남은 기준금액의 30%를 일반기부금 한도로 삼는다.
        BigDecimal generalDonationLimit = donationBaseAmount.subtract(specialDonationAllowed)
                .max(BigDecimal.ZERO).multiply(new BigDecimal("0.30"));
        BigDecimal generalDonationAllowed = input.getGeneralDonationAmount().min(generalDonationLimit);

        // ④ 기부금 입력액 전부가 아닌, 실제 인정된 두 금액만 한 번씩 차감한다.
        BigDecimal incomeAfterDonation = incomeBeforeDonation
                .subtract(specialDonationAllowed).subtract(generalDonationAllowed);

        DonationCalculationDto result = new DonationCalculationDto();
        result.setIncomeBeforeDonation(incomeBeforeDonation);
        result.setDonationBaseAmount(donationBaseAmount);
        result.setSpecialDonationAmount(input.getSpecialDonationAmount());
        result.setSpecialDonationAllowed(specialDonationAllowed);
        result.setGeneralDonationAmount(input.getGeneralDonationAmount());
        result.setGeneralDonationLimit(generalDonationLimit);
        result.setGeneralDonationAllowed(generalDonationAllowed);
        result.setIncomeAfterDonation(incomeAfterDonation);
        return result;
    }

    // 같은 분석의 DB 조건과 기존 총수입·총지출 조회 결과를 모은다.
    @Override
    @Transactional(readOnly = true)
    public TaxCalculationInputDto getCalculationInput(Long taxInputId) {
        TaxCalculationInputDto input = taxMapper.findCalculationInput(taxInputId);
        if (input == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TAX_INPUT_NOT_FOUND", "분석 정보를 찾을 수 없습니다.");
        }

        // 월평균 예상 금액을 포함하는 기존 서비스를 사용하고 세금 계산은 아직 하지 않는다.
        TaxDto income = getTotalIncome(taxInputId);
        TaxDto expense = getTotalExpense(taxInputId);
        input.setTotalIncome(income.getTotalAmount());
        input.setTotalExpense(expense.getTotalAmount());
        return input;
    }

    // 월별 세전 수입을 조회한 뒤 공통 예상 계산을 적용한다.
    @Override
    @Transactional(readOnly = true)
    public List<MonthlyAmountDto> getMonthlyIncomes(Long taxInputId) {
        List<MonthlyAmountDto> incomes = taxMapper.findMonthlyIncomes(taxInputId);
        TransactionPeriodDto period = taxMapper.findTransactionPeriod(taxInputId);
        return addPredictedMonths(incomes, period);
    }

    // 월별 지출을 조회한 뒤 공통 예상 계산을 적용한다.
    @Override
    @Transactional(readOnly = true)
    public List<MonthlyAmountDto> getMonthlyExpenses(Long taxInputId) {
        List<MonthlyAmountDto> expenses = taxMapper.findMonthlyExpenses(taxInputId);
        TransactionPeriodDto period = taxMapper.findTransactionPeriod(taxInputId);
        return addPredictedMonths(expenses, period);
    }

    // 올해 자료의 남은 달에 월평균 예상 금액을 추가한다.
    private List<MonthlyAmountDto> addPredictedMonths(
            List<MonthlyAmountDto> monthlyAmounts, TransactionPeriodDto period) {
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

    // 월별 목록의 금액을 모두 합산한다.
    private BigDecimal sumMonthlyAmounts(List<MonthlyAmountDto> monthlyAmounts) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (MonthlyAmountDto amount : monthlyAmounts) {
            totalAmount = totalAmount.add(BigDecimal.valueOf(amount.getAmount()));
        }
        return totalAmount;
    }

    // 월별 실제·예상 세전 수입 금액을 합산한다.
    @Override
    @Transactional(readOnly = true)
    public TaxDto getTotalIncome(Long taxInputId) {
        List<MonthlyAmountDto> incomes = getMonthlyIncomes(taxInputId);
        BigDecimal totalIncome = sumMonthlyAmounts(incomes);

        return new TaxDto(taxInputId, totalIncome);
    }

    // 월별 실제·예상 지출 금액을 합산한다.
    @Override
    @Transactional(readOnly = true)
    public TaxDto getTotalExpense(Long taxInputId) {
        List<MonthlyAmountDto> expenses = getMonthlyExpenses(taxInputId);
        BigDecimal totalExpense = sumMonthlyAmounts(expenses);

        return new TaxDto(taxInputId, totalExpense);
    }
}
