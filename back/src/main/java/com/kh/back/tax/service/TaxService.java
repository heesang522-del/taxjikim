package com.kh.back.tax.service;

import com.kh.back.tax.dto.TaxCalculationResultDto;

// 세금 분석 결과 처리 기능을 선언한다.
public interface TaxService {
    // 한 번 조회한 월별 목록으로 총액·세금을 계산하고 페이지 데이터를 모은다.
    TaxCalculationResultDto getCalculationResult(Long taxInputId);
}
