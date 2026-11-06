package com.kh.back.tax.mapper;

import com.kh.back.tax.dto.MonthlyTransactionDto;
import com.kh.back.tax.dto.CategoryAmountDto;
import com.kh.back.tax.dto.TaxCalculationInputDto;
import com.kh.back.tax.dto.TransactionPeriodDto;
import com.kh.back.tax.dto.SimulationScenarioDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

// 세금 분석에 필요한 DB 조회 기능을 선언한다.
@Mapper
@Repository("taxCalculationMapper")
public interface TaxMapper {

    List<SimulationScenarioDto> findScenarios(@Param("userId") Long userId);

    int insertScenario(@Param("userId") Long userId, @Param("scenario") SimulationScenarioDto scenario);

    int deleteScenario(@Param("userId") Long userId, @Param("simulationId") Long simulationId);

    // 현재 저장할 분석을 제외한 회원의 이전 저장 분석을 삭제한다.
    int deletePreviousSavedInputs(@Param("userId") Long userId, @Param("taxInputId") Long taxInputId);

    // 같은 분석의 결과 JSON을 저장하거나 덮어쓴다.
    int saveResult(@Param("taxInputId") Long taxInputId, @Param("resultDetail") String resultDetail);

    // 분석의 회원번호와 저장 상태를 함께 기록한다.
    int markResultSaved(@Param("taxInputId") Long taxInputId, @Param("userId") Long userId);

    // 저장한 JSON 원문을 조회한다.
    String findResult(@Param("taxInputId") Long taxInputId);

    // 같은 분석의 귀속연도·가족 수·기부금액을 조회한다.
    TaxCalculationInputDto findCalculationInput(@Param("taxInputId") Long taxInputId);

    // 분석에 저장된 전체 거래의 첫 날짜와 마지막 날짜를 함께 조회한다.
    TransactionPeriodDto findTransactionPeriod(@Param("taxInputId") Long taxInputId);

    // 같은 분석의 수입·지출을 월과 거래 구분으로 묶어 한 번에 조회한다.
    List<MonthlyTransactionDto> findMonthlyTransactions(@Param("taxInputId") Long taxInputId);

    // 실제 거래의 수입·지출 금액을 카테고리별로 조회한다.
    List<CategoryAmountDto> findCategoryAmounts(@Param("taxInputId") Long taxInputId);

}
