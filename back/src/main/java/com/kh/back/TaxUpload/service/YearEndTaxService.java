
package com.kh.back.TaxUpload.service;

import com.kh.back.TaxUpload.dto.PdfParseResponseDto;
import com.kh.back.TaxUpload.dto.TaxTransactionDto;
import com.kh.back.TaxUpload.mapper.TaxMapper;
import com.kh.back.TaxUpload.validator.TaxValidator;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class YearEndTaxService {

    private final TaxValidator taxValidator;

    private final PythonPdfClient pythonPdfClient;

    private final TaxMapper taxMapper;

    /**
     * PDF 업로드 및 거래 추출
     */
    public PdfParseResponseDto parsePdf(
            MultipartFile file
    ) throws IOException {

        byte[] bytes =
                taxValidator.validatePdf(file);

        return pythonPdfClient.analyze(
                bytes,
                file.getOriginalFilename()
        );
    }


    @Transactional
    public void saveConfirmedTransactions(
            Long userId,
            List<TaxTransactionDto> rows
    ) {

        taxValidator.validateUserId(userId);
        taxValidator.validateTransactions(rows);

        for (TaxTransactionDto row : rows) {

            row.setTransactionId(null);

            // 서버에서 확인한 실제 로그인 사용자 ID
            row.setUserId(userId);

            row.setStatus("CONFIRMED");

            taxMapper.insertTransaction(row);
        }
    }


    /**
     * 사용자별 거래 조회
     */
    @Transactional(readOnly = true)
    public List<TaxTransactionDto> getTransactions(
            Long userId
    ) {

        taxValidator.validateUserId(userId);

        return taxMapper.findByUserId(userId);
    }
}
