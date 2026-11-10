package com.kh.back.taxupload.service;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.taxupload.dto.PdfParseResponseDto;
import com.kh.back.taxupload.dto.TaxServiceResponse;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxInputRequest;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxServiceRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface YearEndTaxService {

    TaxServiceResponse save(
            CustomUserDetails principal,
            String guestAccessToken,
            TaxServiceRequest request
    );

    TaxInputRequest update(
            CustomUserDetails principal,
            String guestAccessToken,
            Long taxInputId,
            TaxServiceRequest request
    );

    void delete(
            CustomUserDetails principal,
            String guestAccessToken,
            Long taxInputId,
            TaxServiceRequest request,
            boolean deleteAll
    );

    PdfParseResponseDto parse(
            CustomUserDetails principal,
            String guestAccessToken,
            Long taxInputId,
            MultipartFile file
    ) throws IOException;
}