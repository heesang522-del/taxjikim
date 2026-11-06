package com.kh.back.tax.service;

import com.kh.back.tax.dto.TaxCalculationResultDto;
import tools.jackson.databind.JsonNode;

// 세금 분석 결과 처리 기능을 선언한다.
public interface TaxService {
    // 화면의 결과 전체를 JSON으로 보관한다.
    void saveResult(Long taxInputId, JsonNode result);

    // 저장한 결과 JSON을 조회한다.
    JsonNode getSavedResult(Long taxInputId);

    // 한 번 조회한 월별 목록으로 총액·세금을 계산하고 페이지 데이터를 모은다.
    TaxCalculationResultDto getCalculationResult(Long taxInputId);
}
