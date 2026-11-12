package com.kh.back.report.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 게시글 신고 접수. SecurityConfig에서 /api/v1/posts/** POST는 인증을 요구하므로
// 여기 도달한 요청은 이미 principal이 채워져 있다.
@RestController
@RequestMapping("/api/v1/posts/{postId}/reports")
@RequiredArgsConstructor
public class ReportApiController {
    private final ReportService reportService;

    public record CreateReportRequest(String reasonDetail) {}

    @PostMapping
    public ApiResponse<Void> create(@PathVariable Long postId, @RequestBody CreateReportRequest request,
                                   @AuthenticationPrincipal CustomUserDetails principal) {
        reportService.createPostReport(postId, principal.getUserId(), request.reasonDetail());
        return ApiResponse.ok(null);
    }
}
