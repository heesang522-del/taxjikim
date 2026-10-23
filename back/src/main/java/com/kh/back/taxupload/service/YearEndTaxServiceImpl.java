package com.kh.back.taxupload.service;

import com.kh.back.taxupload.dto.PageOcrResultDto;
import com.kh.back.taxupload.dto.PdfParseResponseDto;
import com.kh.back.taxupload.dto.TaxTransactionDto;
import com.kh.back.taxupload.mapper.TaxMapper;
import com.kh.back.taxupload.validator.TaxValidator;
import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.exception.ApiException;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class YearEndTaxServiceImpl implements YearEndTaxService {

    private final TaxValidator taxValidator;
    private final TaxOcrService taxOcrService;
    private final PythonPdfClient pythonPdfClient;
    private final TaxMapper taxMapper;

    // 저장하는 Mapper / repo

    @Override
    public PdfParseResponseDto parsePdf(
            MultipartFile file
    ) throws IOException {
        byte[] bytes = taxValidator.validatePdf(file);

        List<PageOcrResultDto> pages =
                taxOcrService.extractPages(bytes);

        PdfParseResponseDto parsedDTO = pythonPdfClient.analyze(
                bytes,
                file.getOriginalFilename(),
                pages
        );

        // 저장 로직


        return parsedDTO;
    }

    @Override
    @Transactional
    public void saveConfirmedTransactions(
            CustomUserDetails principal,
            Long requestedUserId,
            List<TaxTransactionDto> rows
    ) {
        Long targetUserId = taxValidator.resolveTargetUserId(
                principal,
                requestedUserId
        );

        requireActiveUser(targetUserId);
        taxValidator.validateTransactions(rows);

        for (TaxTransactionDto row : rows) {
            int taxYear = row.getTransactionDate().getYear();

            Long taxInputId = requireTaxInput(
                    targetUserId,
                    taxYear
            );

            // 요청 본문에 담긴 사용자와 세무 입력 ID는 사용하지 않는다.
            row.setTransactionId(null);
            row.setUserId(targetUserId);
            row.setTaxInputId(taxInputId);
            row.setTaxYear(taxYear);

            if (row.getEdited() == null) {
                row.setEdited(false);
            }

            taxMapper.insertTransaction(row);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxTransactionDto> getTransactions(
            CustomUserDetails principal,
            Long requestedUserId
    ) {
        Long targetUserId = taxValidator.resolveTargetUserId(
                principal,
                requestedUserId
        );

        requireActiveUser(targetUserId);

        return taxMapper.findByUserId(targetUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public TaxTransactionDto getTransaction(
            CustomUserDetails principal,
            Long transactionId
    ) {
        taxValidator.requireLogin(principal);
        taxValidator.validateTransactionId(transactionId);

        TaxTransactionDto transaction =
                taxMapper.findById(transactionId);

        taxValidator.requireOwnerOrAdmin(
                principal,
                transaction
        );

        return transaction;
    }

    @Override
    @Transactional
    public void updateTransaction(
            CustomUserDetails principal,
            Long transactionId,
            TaxTransactionDto request
    ) {
        taxValidator.requireLogin(principal);
        taxValidator.validateTransactionId(transactionId);

        TaxTransactionDto existing =
                taxMapper.lockById(transactionId);

        taxValidator.requireOwnerOrAdmin(
                principal,
                existing
        );

        taxValidator.validateTransaction(request);

        int taxYear = request.getTransactionDate().getYear();

        Long taxInputId = requireTaxInput(
                existing.getUserId(),
                taxYear
        );

        // 관리자가 수정해도 거래 소유자는 변경하지 않는다.
        request.setTransactionId(existing.getTransactionId());
        request.setUserId(existing.getUserId());
        request.setTaxInputId(taxInputId);
        request.setTaxYear(taxYear);
        request.setEdited(true);

        taxMapper.updateTransaction(request);
    }

    @Override
    @Transactional
    public void deleteTransaction(
            CustomUserDetails principal,
            Long transactionId
    ) {
        taxValidator.requireLogin(principal);
        taxValidator.validateTransactionId(transactionId);

        TaxTransactionDto existing =
                taxMapper.lockById(transactionId);

        taxValidator.requireOwnerOrAdmin(
                principal,
                existing
        );

        taxMapper.softDeleteTransaction(
                existing.getTransactionId(),
                existing.getUserId()
        );
    }

    private void requireActiveUser(Long userId) {
        Long existingUserId = taxMapper.findActiveUserId(userId);

        if (existingUserId == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "USER_NOT_FOUND",
                    "대상 사용자를 찾을 수 없습니다."
            );
        }
    }

    private Long requireTaxInput(
            Long userId,
            int taxYear
    ) {
        Long taxInputId =
                taxMapper.findTaxInputIdByUserAndYear(
                        userId,
                        taxYear
                );

        if (taxInputId == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "TAX_INPUT_REQUIRED",
                    taxYear + "년 세무 기본정보를 먼저 등록해주세요."
            );
        }

        return taxInputId;
    }
}