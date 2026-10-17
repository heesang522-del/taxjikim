package com.kh.back.TaxUpload.controller;

import com.kh.back.TaxUpload.dto.PdfParseResponseDto;
import com.kh.back.TaxUpload.dto.TaxTransactionDto;
import com.kh.back.TaxUpload.service.YearEndTaxService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/tax")
@RequiredArgsConstructor
public class TaxUploadController {

    private final YearEndTaxService yearEndTaxService;

    @PostMapping(
            value = "/parse",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<PdfParseResponseDto> parsePdf(
            @RequestParam("file") MultipartFile file
    ) throws IOException {

        return ResponseEntity.ok(
                yearEndTaxService.parsePdf(file)
        );
    }

    @PostMapping("/transactions")
    public ResponseEntity<Void> saveTransactions(
            Principal principal,
            @RequestBody List<TaxTransactionDto> transactions
    ) {

        Long userId = authenticatedUserId(principal);

        yearEndTaxService.saveConfirmedTransactions(
                userId, transactions
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TaxTransactionDto>> getTransactions(
            Principal principal
    ) {

        Long userId = authenticatedUserId(principal);

        return ResponseEntity.ok(
                yearEndTaxService.getTransactions(userId)
        );
    }

    private Long authenticatedUserId(Principal principal) {

        if (principal == null) {
            throw new IllegalStateException(
                    "인증이 필요합니다."
            );
        }

        // 인증 주체의 name이 숫자 userId인 경우의 예시
        return Long.valueOf(principal.getName());
    }
}