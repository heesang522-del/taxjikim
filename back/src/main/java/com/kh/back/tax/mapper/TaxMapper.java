package com.kh.back.tax.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

// 세금 분석에 필요한 DB 조회 기능을 선언한다.
@Mapper
@Repository("taxCalculationMapper")
public interface TaxMapper {

    // 분석번호에 해당하는 실제 지출 금액을 합산한다.
    BigDecimal findTotalExpense(@Param("taxInputId") Long taxInputId);

    // 분석번호에 해당하는 세전 수입 금액을 합산한다.
    BigDecimal findTotalIncome(@Param("taxInputId") Long taxInputId);
}
