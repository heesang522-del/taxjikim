package com.kh.back.TaxUpload.service;

import com.kh.back.TaxUpload.dto.PdfParseResponseDto;
import com.kh.back.TaxUpload.dto.TaxTransactionDto;
import com.kh.back.auth.security.CustomUserDetails;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface YearEndTaxService {

    PdfParseResponseDto parsePdf(
            MultipartFile file
    ) throws IOException;

    void saveConfirmedTransactions(
            CustomUserDetails principal,
            Long requestedUserId,
            List<TaxTransactionDto> rows
    );

    List<TaxTransactionDto> getTransactions(
            CustomUserDetails principal,
            Long requestedUserId
    );

    TaxTransactionDto getTransaction(
            CustomUserDetails principal,
            Long transactionId
    );

    void updateTransaction(
            CustomUserDetails principal,
            Long transactionId,
            TaxTransactionDto request
    );

    void deleteTransaction(
            CustomUserDetails principal,
            Long transactionId
    );
}