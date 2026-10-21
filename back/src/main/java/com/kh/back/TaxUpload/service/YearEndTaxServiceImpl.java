package com.kh.back.TaxUpload.service;

import com.kh.back.common.exception.ApiException;

import com.kh.back.TaxUpload.dto.PageOcrResultDto;
import com.kh.back.TaxUpload.dto.PdfParseResponseDto;
import com.kh.back.TaxUpload.dto.TaxTransactionDto;

import com.kh.back.TaxUpload.mapper.TaxMapper;
import com.kh.back.TaxUpload.validator.TaxValidator;

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

    @Override
    public PdfParseResponseDto parsePdf(
            MultipartFile file
    ) throws IOException {

        // 기존 비회원 PDF 분석 기능을 유지한다.
        byte[] bytes = taxValidator.validatePdf(file);

        List<PageOcrResultDto> pages =
                taxOcrService.extractPages(bytes);

        return pythonPdfClient.analyze(
                bytes,
                file.getOriginalFilename(),
                pages
        );
    }

    @Override
    @Transactional
    public void saveConfirmedTransactions(
            Long requestedUserId,
            List<TaxTransactionDto> rows
    ) {

        Long targetUserId =
                taxValidator.resolveTargetUserId(
                        requestedUserId
                );

        requireActiveUser(targetUserId);

        // 모든 거래 입력을 먼저 검증한다.
        taxValidator.validateTransactions(rows);

        for (TaxTransactionDto row : rows) {

            int taxYear =
                    row.getTransactionDate().getYear();

            Long taxInputId = requireTaxInput(
                    targetUserId,
                    taxYear
            );

            /*
             * 사용자와 세무 입력 ID는 요청값을 신뢰하지 않는다.
             * 인증·권한 검사와 DB 조회 결과로 확정한다.
             */
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
            Long requestedUserId
    ) {

        Long targetUserId =
                taxValidator.resolveTargetUserId(
                        requestedUserId
                );

        requireActiveUser(targetUserId);

        return taxMapper.findByUserId(targetUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public TaxTransactionDto getTransaction(
            Long transactionId
    ) {

        taxValidator.requireLogin();
        taxValidator.validateTransactionId(transactionId);

        TaxTransactionDto transaction =
                taxMapper.findById(transactionId);

        taxValidator.requireOwnerOrAdmin(transaction);

        return transaction;
    }

    @Override
    @Transactional
    public void updateTransaction(
            Long transactionId,
            TaxTransactionDto request
    ) {

        taxValidator.requireLogin();
        taxValidator.validateTransactionId(transactionId);

        TaxTransactionDto existing =
                taxMapper.lockById(transactionId);

        // 요청의 userId가 아닌 기존 거래의 실제 소유자로 검사한다.
        taxValidator.requireOwnerOrAdmin(existing);

        taxValidator.validateTransaction(request);

        int taxYear =
                request.getTransactionDate().getYear();

        Long taxInputId = requireTaxInput(
                existing.getUserId(),
                taxYear
        );

        // 관리자가 수정하더라도 원래 거래 소유자를 유지한다.
        request.setTransactionId(existing.getTransactionId());
        request.setUserId(existing.getUserId());
        request.setTaxInputId(taxInputId);
        request.setTaxYear(taxYear);

        // 수정 API로 변경한 내역임을 서버에서 기록한다.
        request.setEdited(true);

        taxMapper.updateTransaction(request);
    }

    @Override
    @Transactional
    public void deleteTransaction(
            Long transactionId
    ) {

        taxValidator.requireLogin();
        taxValidator.validateTransactionId(transactionId);

        TaxTransactionDto existing =
                taxMapper.lockById(transactionId);

        taxValidator.requireOwnerOrAdmin(existing);

        // 실제 데이터를 삭제하지 않고 삭제 시각을 기록한다.
        taxMapper.softDeleteTransaction(
                existing.getTransactionId(),
                existing.getUserId()
        );
    }

    private void requireActiveUser(Long userId) {

        Long existingUserId =
                taxMapper.findActiveUserId(userId);

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
            /*
             * REGION, JOB, INCOME_TYPE을 임의로 채워서
             * 세무 기본정보를 생성하지 않는다.
             */
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "TAX_INPUT_REQUIRED",
                    taxYear + "년 세무 기본정보를 먼저 등록해주세요."
            );
        }

        return taxInputId;
    }
}