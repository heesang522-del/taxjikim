package com.kh.back.tax.mapper;

import com.kh.back.tax.dto.MonthlyTransactionDto;
import com.kh.back.tax.dto.TaxCalculationInputDto;
import com.kh.back.tax.dto.TransactionPeriodDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

// 세금 분석에 필요한 DB 조회 기능을 선언한다.
@Mapper
@Repository("taxCalculationMapper")
public interface TaxMapper {

    // 같은 분석의 귀속연도·가족 수·기부금액을 조회한다.
    TaxCalculationInputDto findCalculationInput(@Param("taxInputId") Long taxInputId);

    // 분석에 저장된 전체 거래의 첫 날짜와 마지막 날짜를 함께 조회한다.
    TransactionPeriodDto findTransactionPeriod(@Param("taxInputId") Long taxInputId);

    // 같은 분석의 수입·지출을 월과 거래 구분으로 묶어 한 번에 조회한다.
    List<MonthlyTransactionDto> findMonthlyTransactions(@Param("taxInputId") Long taxInputId);

}
