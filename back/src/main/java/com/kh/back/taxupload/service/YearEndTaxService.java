package com.kh.back.taxupload.service;

import com.kh.back.taxupload.dto.PdfParseResponseDto;
import com.kh.back.taxupload.dto.TaxTransactionDto;
import com.kh.back.taxupload.dto.TaxTransactionDto.AccessContext;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxInputRequest;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxResultResponse;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface YearEndTaxService {

    TaxInputRequest createInput(
            AccessContext access,
            TaxInputRequest request
    );

    TaxInputRequest getInput(
            AccessContext access,
            Long taxInputId
    );

    List<TaxInputRequest> getSavedInputs(
            AccessContext access,
            Integer taxYear
    );

    TaxInputRequest updateInput(
            AccessContext access,
            Long taxInputId,
            TaxInputRequest request
    );

    void deleteInput(
            AccessContext access,
            Long taxInputId
    );

    List<TaxTransactionDto> saveTransactions(
            AccessContext access,
            Long taxInputId,
            List<TaxTransactionDto> rows
    );

    List<TaxTransactionDto> getTransactions(
            AccessContext access,
            Long taxInputId
    );

    void updateCategory(
            AccessContext access,
            Long taxInputId,
            Long transactionId,
            String category
    );

    void deleteTransaction(
            AccessContext access,
            Long taxInputId,
            Long transactionId
    );

    TaxResultResponse getResult(
            AccessContext access,
            Long taxInputId
    );

    TaxResultResponse calculateInput(
            AccessContext access,
            Long taxInputId
    );

    PdfParseResponseDto parsePdf(
            AccessContext access,
            Long taxInputId,
            MultipartFile file
    ) throws IOException;
}