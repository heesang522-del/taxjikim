package com.kh.back.report.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.report.dto.ReportDto;
import com.kh.back.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// SecurityConfig가 /api/v1/admin/** 전체를 hasRole(ADMIN)으로 막아두므로 여기서 별도 권한 검사는 하지 않는다.
@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
public class AdminReportApiController {
    private final ReportService reportService;

    public record ResolveReportRequest(String result) {}

    @GetMapping
    public ApiResponse<List<ReportDto>> list() {
        return ApiResponse.ok(reportService.getReports());
    }

    @PatchMapping("/{reportId}")
    public ApiResponse<Void> resolve(@PathVariable Long reportId, @RequestBody ResolveReportRequest request,
                                    @AuthenticationPrincipal CustomUserDetails principal) {
        reportService.resolveReport(reportId, request.result(), principal.getUserId());
        return ApiResponse.ok(null);
    }
}
