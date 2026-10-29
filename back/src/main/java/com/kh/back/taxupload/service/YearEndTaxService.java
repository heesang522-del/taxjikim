package com.kh.back.taxupload.service;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.taxupload.dto.PdfParseResponseDto;
import com.kh.back.taxupload.dto.TaxTransactionDto;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxInputRequest;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxResultResponse;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxServiceRequest;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxServiceResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public interface YearEndTaxService {

    TaxInputRequest save(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            TaxInputRequest request
    );

    TaxServiceResponse get(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            TaxServiceRequest request
    );

    List<TaxTransactionDto> transaction(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            Long taxInputId,
            List<TaxTransactionDto> rows
    );

    TaxInputRequest update(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            TaxServiceRequest request
    );

    void delete(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            TaxServiceRequest request
    );



    PdfParseResponseDto parsePdf(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            Long taxInputId,
            MultipartFile file
    ) throws IOException;
}