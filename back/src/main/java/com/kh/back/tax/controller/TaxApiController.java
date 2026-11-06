package com.kh.back.tax.controller;

import com.kh.back.common.dto.ApiResponse;
import com.kh.back.tax.dto.TaxCalculationResultDto;
import com.kh.back.tax.service.TaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import com.kh.back.tax.dto.TaxSimulationDto;
import com.kh.back.tax.dto.TaxBreakdownDto;
import org.springframework.web.bind.annotation.PostMapping;


// 세금 분석 결과 요청을 받는다.
@RestController
@RequestMapping("/api/v1/tax/inputs")
@RequiredArgsConstructor
public class TaxApiController {

    private final TaxService taxService;

    // 결과 데이터에 추가 가정을 반영해 계산하며 DB에는 저장하지 않는다.
    @PostMapping("/simulation")
    public ApiResponse<TaxBreakdownDto> simulate(@RequestBody TaxSimulationDto request) {
        return ApiResponse.ok(taxService.simulate(request));
    }

    // 페이지에서 보낸 결과 JSON을 저장한다. 로그인 접근은 SecurityConfig에서 관리한다.
    @PutMapping("/{taxInputId}/result")
    public ResponseEntity<Void> saveResult(@PathVariable("taxInputId") Long taxInputId,
                                         @RequestBody JsonNode result) {
        taxService.saveResult(taxInputId, result);
        return ResponseEntity.noContent().build();
    }

    // 재계산 없이 저장 당시의 JSON을 반환한다.
    @GetMapping("/{taxInputId}/result")
    public ApiResponse<JsonNode> getSavedResult(@PathVariable("taxInputId") Long taxInputId) {
        return ApiResponse.ok(taxService.getSavedResult(taxInputId));
    }

    // 결과 페이지에 필요한 산출 근거·월별 금액·사업자등록 여부를 함께 반환한다.
    @GetMapping("/{taxInputId}/calculation-result")
    public ApiResponse<TaxCalculationResultDto> getCalculationResult(@PathVariable("taxInputId") Long taxInputId) {
        return ApiResponse.ok(taxService.getCalculationResult(taxInputId));
    }

}
