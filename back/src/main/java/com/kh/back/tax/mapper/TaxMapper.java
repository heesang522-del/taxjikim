package com.kh.back.tax.mapper;

import com.kh.back.tax.dto.MonthlyAmountDto;
import com.kh.back.tax.dto.TransactionPeriodDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

// 세금 분석에 필요한 DB 조회 기능을 선언한다.
@Mapper
@Repository("taxCalculationMapper")
public interface TaxMapper {

    // 분석에 저장된 전체 거래의 첫 날짜와 마지막 날짜를 함께 조회한다.
    TransactionPeriodDto findTransactionPeriod(@Param("taxInputId") Long taxInputId);

    // 같은 분석의 세전 수입을 월별로 합산한다.
    List<MonthlyAmountDto> findMonthlyIncomes(@Param("taxInputId") Long taxInputId);

    // 같은 분석의 실제 지출을 연도와 월별로 합산한다.
    List<MonthlyAmountDto> findMonthlyExpenses(@Param("taxInputId") Long taxInputId);

    // 분석번호에 해당하는 실제 지출 금액을 합산한다.
    BigDecimal findTotalExpense(@Param("taxInputId") Long taxInputId);
}
