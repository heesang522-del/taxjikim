package com.kh.back.TaxUpload.service;

import com.kh.back.TaxUpload.dto.PdfParseResponseDto;
import com.kh.back.TaxUpload.dto.TaxTransactionDto;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface YearEndTaxService {

    PdfParseResponseDto parsePdf(
            MultipartFile file
    ) throws IOException;

    void saveConfirmedTransactions(
            Long requestedUserId,
            List<TaxTransactionDto> rows
    );

    List<TaxTransactionDto> getTransactions(
            Long requestedUserId
    );

    TaxTransactionDto getTransaction(
            Long transactionId
    );

    void updateTransaction(
            Long transactionId,
            TaxTransactionDto request
    );

    void deleteTransaction(
            Long transactionId
    );
}