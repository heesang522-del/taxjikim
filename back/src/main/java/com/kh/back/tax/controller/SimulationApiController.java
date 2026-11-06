package com.kh.back.tax.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.tax.dto.SimulationScenarioDto;
import com.kh.back.tax.service.TaxService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 로그인 접근은 SecurityConfig에서 관리하고 회원번호는 인증 정보에서 가져온다.
@RestController
@RequestMapping("/api/v1/tax/simulations")
@RequiredArgsConstructor
public class SimulationApiController {
    private final TaxService taxService;

    // 현재 회원이 DB에 저장한 시나리오 목록을 반환한다.
    @GetMapping
    public ApiResponse<List<SimulationScenarioDto>> getScenarios(@AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(taxService.getScenarios(principal.getUserId()));
    }

    // 추가 가정과 결과 합계를 기존 SIMULATION 테이블에 저장한다.
    @PostMapping
    public ApiResponse<SimulationScenarioDto> saveScenario(@AuthenticationPrincipal CustomUserDetails principal,
                                                          @RequestBody SimulationScenarioDto scenario) {
        return ApiResponse.ok(taxService.saveScenario(principal.getUserId(), scenario));
    }

    // 회원번호와 시나리오번호가 함께 일치하는 기록만 삭제한다.
    @DeleteMapping("/{simulationId}")
    public ResponseEntity<Void> deleteScenario(@AuthenticationPrincipal CustomUserDetails principal,
                                               @PathVariable("simulationId") Long simulationId) {
        taxService.deleteScenario(principal.getUserId(), simulationId);
        return ResponseEntity.noContent().build();
    }
}
