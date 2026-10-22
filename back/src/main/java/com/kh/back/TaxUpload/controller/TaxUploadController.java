package com.kh.back.taxupload.controller;

import com.kh.back.taxupload.dto.PdfParseResponseDto;
import com.kh.back.taxupload.dto.TaxTransactionDto;
import com.kh.back.taxupload.service.YearEndTaxService;
import com.kh.back.auth.security.CustomUserDetails;

import com.kh.back.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/tax")
@RequiredArgsConstructor
public class TaxUploadController {

    private final YearEndTaxService service;

    /**
     * 비회원도 PDF를 분석할 수 있다.
     * 분석 결과를 DB에 저장하지 않는다.
     */
    @PostMapping(
            value = "/parse",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse<PdfParseResponseDto> parse(
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return ApiResponse.ok(service.parsePdf(file));
    }

    /**
     * 본인 거래를 저장한다.
     * 다른 사용자 지정은 TaxValidator에서 관리자 권한을 검사한다.
     */
    @PostMapping("/transactions")
    public ResponseEntity<Void> save(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(value = "userId", required = false)
            Long requestedUserId,
            @RequestBody List<TaxTransactionDto> rows
    ) {
        service.saveConfirmedTransactions(
                principal,
                requestedUserId,
                rows
        );

        return ResponseEntity.noContent().build();
    }

    /**
     * userId를 생략하면 로그인 사용자의 거래를 조회한다.
     */
    @GetMapping("/transactions")
    public ApiResponse<List<TaxTransactionDto>> getTransactions(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(value = "userId", required = false)
            Long requestedUserId
    ) {
        return ApiResponse.ok(service.getTransactions(
                principal,
                requestedUserId)
        );
    }

    /**
     * 거래 소유자 또는 관리자만 조회할 수 있다.
     */
    @GetMapping("/transactions/{transactionId}")
    public ApiResponse<TaxTransactionDto>getTransaction(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable("transactionId") Long transactionId
    ) {
        return ApiResponse.ok(service.getTransaction(
                principal,
                transactionId)
        );
    }

    /**
     * 거래 소유자 또는 관리자만 수정할 수 있다.
     */
    @PutMapping("/transactions/{transactionId}")
    public ResponseEntity<Void> updateTransaction(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable("transactionId") Long transactionId,
            @RequestBody TaxTransactionDto request
    ) {
        service.updateTransaction(
                principal,
                transactionId,
                request
        );

        return ResponseEntity.noContent().build();
    }

    /**
     * 거래 소유자 또는 관리자만 삭제할 수 있다.
     */
    @DeleteMapping("/transactions/{transactionId}")
    public ResponseEntity<Void> deleteTransaction(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable("transactionId") Long transactionId
    ) {
        service.deleteTransaction(
                principal,
                transactionId
        );

        return ResponseEntity.noContent().build();
    }
}