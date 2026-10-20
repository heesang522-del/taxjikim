
package com.kh.back.TaxUpload.validator;

import com.kh.back.TaxUpload.dto.TaxTransactionDto;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@Component
public class TaxValidator {

    public byte[] validatePdf(
            MultipartFile file
    ) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "PDF 파일이 비어 있습니다."
            );
        }

        byte[] bytes = file.getBytes();

        if (
                bytes.length < 5
                        || bytes[0] != '%'
                        || bytes[1] != 'P'
                        || bytes[2] != 'D'
                        || bytes[3] != 'F'
                        || bytes[4] != '-'
        ) {
            throw new IllegalArgumentException(
                    "PDF 형식이 아닙니다."
            );
        }

        return bytes;
    }

    public void validateUserId(Long id) {

        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "유효하지 않은 사용자 ID입니다."
            );
        }
    }

    public void validateTransactions(
            List<TaxTransactionDto> rows
    ) {

        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "저장할 거래가 없습니다."
            );
        }

        for (TaxTransactionDto t : rows) {

            if (
                    t == null
                            || t.getTransactionDate() == null
                            || t.getAmount() == null
                            || t.getAmount().compareTo(BigDecimal.ZERO) < 0
                            || !(
                            "INCOME".equals(t.getTransactionType())
                                    || "EXPENSE".equals(t.getTransactionType())
                    )
                            || t.getDescription() == null
                            || t.getDescription().isBlank()
            ) {
                throw new IllegalArgumentException(
                        "거래 날짜, 구분, 금액, 설명을 확인하세요."
                );
            }
        }
    }
}
