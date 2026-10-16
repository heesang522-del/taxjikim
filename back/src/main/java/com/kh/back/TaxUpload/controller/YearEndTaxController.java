package com.kh.back.TaxUpload.controller;

import java.util.Map;
import com.kh.back.TaxUpload.service.YearEndTaxService;
import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/tax")
public class YearEndTaxController {
    private final YearEndTaxService yearEndTaxService;
    public YearEndTaxController(YearEndTaxService yearEndTaxService) { this.yearEndTaxService = yearEndTaxService; }

    @PostMapping("/upload-pdf")
    public ApiResponse<Map<String, Object>> uploadTaxPdf(@RequestParam("file") MultipartFile file,
            @RequestParam("taxYear") int taxYear, @AuthenticationPrincipal CustomUserDetails principal) throws IOException {
        if (taxYear < 1900 || taxYear > 9999) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "귀속연도를 확인해 주세요.");
        }
        // 클라이언트가 지정한 회원 번호나 세무 입력 ID는 사용하지 않는다.
        Long userId = principal == null ? null : principal.getUserId();
        return ApiResponse.ok(yearEndTaxService.uploadPdf(file, userId, taxYear));
    }

    @PostMapping("/upload-ledger-pdf")
    public ApiResponse<Map<String, Object>> uploadLedgerPdf(@RequestParam("file") MultipartFile file,
            @RequestParam("taxYear") int taxYear, @AuthenticationPrincipal CustomUserDetails principal) throws IOException {
        validateYear(taxYear);
        return ApiResponse.ok(yearEndTaxService.uploadLedgerPdf(file, principal == null ? null : principal.getUserId(), taxYear));
    }

    @PostMapping("/upload-evidence-pdf")
    public ApiResponse<Map<String, Object>> uploadEvidencePdf(@RequestParam("file") MultipartFile file,
            @RequestParam("taxYear") int taxYear, @AuthenticationPrincipal CustomUserDetails principal) throws IOException {
        validateYear(taxYear);
        return ApiResponse.ok(yearEndTaxService.uploadEvidencePdf(file, principal == null ? null : principal.getUserId(), taxYear));
    }

    @PostMapping("/reviewed-transactions")
    public ApiResponse<com.kh.back.TaxUpload.dto.TaxTransactionDto> saveReviewedTransaction(
            @RequestBody com.kh.back.TaxUpload.dto.TaxTransactionDto.ReviewedTransactionRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(yearEndTaxService.saveReviewedTransaction(request, principal == null ? null : principal.getUserId()));
    }

    @PutMapping("/reviewed-transactions")
    public ApiResponse<com.kh.back.TaxUpload.dto.TaxTransactionDto> updateReviewedTransaction(
            @RequestBody com.kh.back.TaxUpload.dto.TaxTransactionDto.ReviewedTransactionRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(yearEndTaxService.updateReviewedTransaction(request, principal == null ? null : principal.getUserId()));
    }

    private void validateYear(int year) {
        if (year < 1900 || year > 9999) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "귀속연도를 확인해 주세요.");
    }
}
