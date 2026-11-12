package com.kh.back.tax.service;

import com.kh.back.tax.dto.TaxCalculationResultDto;
import tools.jackson.databind.JsonNode;
import com.kh.back.tax.dto.TaxSimulationDto;
import com.kh.back.tax.dto.TaxBreakdownDto;
import com.kh.back.tax.dto.SimulationScenarioDto;
import com.kh.back.tax.dto.SavedTaxResultDto;
import java.util.List;

// 세금 분석 결과 처리 기능을 선언한다.
public interface TaxService {
    // 재계산 없이 본인의 저장 결과 목록을 조회한다.
    List<SavedTaxResultDto> getSavedResults(Long userId);

    // 로그인 회원의 특정 분석에 속한 시나리오 목록을 조회한다.
    List<SimulationScenarioDto> getScenarios(Long userId, Long taxInputId);

    // 추가 가정과 저장 당시 결과 합계를 보관한다.
    SimulationScenarioDto saveScenario(Long userId, SimulationScenarioDto scenario);

    // 본인 소유의 시나리오를 삭제한다.
    void deleteScenario(Long userId, Long simulationId);

    // 추가 수입·지출·기부금을 기존 세금 계산 순서에 반영한다.
    TaxBreakdownDto simulate(TaxSimulationDto request);

    // 화면의 결과 JSON과 해당 분석의 회원번호를 함께 보관한다.
    void saveResult(Long taxInputId, Long userId, JsonNode result);

    // 분석 소유자를 확인한 뒤 저장 결과 JSON을 조회한다.
    JsonNode getSavedResult(Long taxInputId, Long userId);

    // 한 번 조회한 월별 목록으로 총액·세금을 계산하고 페이지 데이터를 모은다.
    TaxCalculationResultDto getCalculationResult(Long taxInputId, Long userId);
}
