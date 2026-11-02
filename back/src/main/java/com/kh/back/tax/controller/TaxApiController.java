package com.kh.back.tax.controller;

import com.kh.back.common.dto.ApiResponse;
import com.kh.back.tax.dto.MonthlyAmountDto;
import com.kh.back.tax.dto.TaxBreakdownDto;
import com.kh.back.tax.service.TaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 세금 분석 결과 요청을 받는다.
@RestController
@RequestMapping("/api/v1/tax/inputs")
@RequiredArgsConstructor
public class TaxApiController {

    private final TaxService taxService;

    // 같은 분석의 총세금과 전체 산출 근거를 함께 조회한다.
    @GetMapping("/{taxInputId}/breakdown")
    public ApiResponse<TaxBreakdownDto> getTaxBreakdown(@PathVariable("taxInputId") Long taxInputId) {
        return ApiResponse.ok(taxService.getTaxBreakdown(taxInputId));
    }

    // 분석번호에 해당하는 월별 실제·예상 세전 수입을 조회한다.
    @GetMapping("/{taxInputId}/monthly-incomes")
    public ApiResponse<List<MonthlyAmountDto>> getMonthlyIncomes(@PathVariable("taxInputId") Long taxInputId) {
        return ApiResponse.ok(taxService.getMonthlyIncomes(taxInputId));
    }

    // 분석번호에 해당하는 월별 실제 지출을 조회한다.
    @GetMapping("/{taxInputId}/monthly-expenses")
    public ApiResponse<List<MonthlyAmountDto>> getMonthlyExpenses(@PathVariable("taxInputId") Long taxInputId) {
        return ApiResponse.ok(taxService.getMonthlyExpenses(taxInputId));
    }

}
