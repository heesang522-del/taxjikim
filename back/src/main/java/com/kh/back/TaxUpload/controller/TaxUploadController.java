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
import java.util.List;

@RestController
@RequestMapping("/api/v1/tax")
@RequiredArgsConstructor
public class TaxUploadController {

    private final YearEndTaxService service;

    /**
     * 기존 비회원 PDF 분석 기능을 유지한다.
     * 거래 DB 저장은 이 API에서 수행하지 않는다.
     */
    @PostMapping(
            value = "/parse",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public PdfParseResponseDto parse(
            @RequestParam("file") MultipartFile file
    ) throws IOException {

        return service.parsePdf(file);
    }

    /**
     * 일반 사용자: 본인 거래 저장
     * 관리자: userId를 지정해 대상 사용자 거래 저장 가능
     */
    @PostMapping("/transactions")
    public ResponseEntity<Void> save(
            @RequestParam(
                    value = "userId",
                    required = false
            )
            Long userId,

            @RequestBody List<TaxTransactionDto> rows
    ) {

        service.saveConfirmedTransactions(
                userId,
                rows
        );

        return ResponseEntity.noContent().build();
    }

    /**
     * userId를 생략하면 본인 거래를 조회한다.
     * 다른 사용자 지정은 관리자만 허용한다.
     */
    @GetMapping("/transactions")
    public List<TaxTransactionDto> getTransactions(
            @RequestParam(
                    value = "userId",
                    required = false
            )
            Long userId
    ) {

        return service.getTransactions(userId);
    }

    /**
     * 관리자 또는 거래 소유자만 단건 조회 가능
     */
    @GetMapping("/transactions/{transactionId}")
    public TaxTransactionDto getTransaction(
            @PathVariable Long transactionId
    ) {

        return service.getTransaction(transactionId);
    }

    /**
     * 관리자 또는 거래 소유자만 수정 가능
     */
    @PutMapping("/transactions/{transactionId}")
    public ResponseEntity<Void> updateTransaction(
            @PathVariable Long transactionId,
            @RequestBody TaxTransactionDto request
    ) {

        service.updateTransaction(
                transactionId,
                request
        );

        return ResponseEntity.noContent().build();
    }

    /**
     * 관리자 또는 거래 소유자만 삭제 가능
     */
    @DeleteMapping("/transactions/{transactionId}")
    public ResponseEntity<Void> deleteTransaction(
            @PathVariable Long transactionId
    ) {

        service.deleteTransaction(transactionId);

        return ResponseEntity.noContent().build();
    }
}