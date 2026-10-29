package com.kh.back.tax.mapper;

import com.kh.back.tax.dto.MonthlyExpenseDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// 세금 분석에 필요한 DB 조회 기능을 선언한다.
@Mapper
@Repository("taxCalculationMapper")
public interface TaxMapper {

    // 분석에 저장된 전체 거래 중 첫 날짜를 조회한다.
    LocalDate findFirstTransactionDate(@Param("taxInputId") Long taxInputId);

    // 분석에 저장된 전체 거래 중 마지막 날짜를 조회한다.
    LocalDate findLastTransactionDate(@Param("taxInputId") Long taxInputId);

    // 같은 분석의 실제 지출을 연도와 월별로 합산한다.
    List<MonthlyExpenseDto> findMonthlyExpenses(@Param("taxInputId") Long taxInputId);

    // 분석번호에 해당하는 실제 지출 금액을 합산한다.
    BigDecimal findTotalExpense(@Param("taxInputId") Long taxInputId);

    // 분석번호에 해당하는 세전 수입 금액을 합산한다.
    BigDecimal findTotalIncome(@Param("taxInputId") Long taxInputId);
}
