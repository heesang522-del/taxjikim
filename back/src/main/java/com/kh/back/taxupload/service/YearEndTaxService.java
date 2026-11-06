package com.kh.back.taxupload.service;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.taxupload.dto.PdfParseResponseDto;
import com.kh.back.taxupload.dto.TaxServiceResponse;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxInputRequest;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxServiceRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

public interface YearEndTaxService {

    // 분석 기본정보 생성과 거래 최초 저장을 처리한다.
    TaxServiceResponse save(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            TaxServiceRequest request
    );

    // 기본정보와 카테고리를 한 요청에서 수정한다.
    TaxInputRequest update(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            Long taxInputId,
            TaxServiceRequest request
    );

    // 분석 전체 또는 거래 한 건을 삭제한다.
    void delete(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            Long taxInputId,
            TaxServiceRequest request,
            boolean deleteAll
    );

    // PDF에서 거래 내역을 추출한다.
    PdfParseResponseDto parse(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            Long taxInputId,
            MultipartFile file
    ) throws IOException;
}