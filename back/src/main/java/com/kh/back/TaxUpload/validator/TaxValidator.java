package com.kh.back.TaxUpload.validator;

import com.kh.back.TaxUpload.dto.TaxTransactionDto;
import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.exception.ApiException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class TaxValidator {

    private static final BigDecimal MAX_AMOUNT =
            new BigDecimal("9999999999999.99");

    /**
     * 기존 JWT 인증에서 전달한 회원 정보를 확인한다.
     * JWT 토큰을 여기서 다시 검증하거나 파싱하지 않는다.
     */
    public Long requireLogin(CustomUserDetails principal) {
        if (principal == null
                || principal.getUserId() == null
                || principal.getUserId() <= 0) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "로그인이 필요합니다."
            );
        }

        return principal.getUserId();
    }

    public boolean isAdmin(CustomUserDetails principal) {
        requireLogin(principal);

        return principal.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(authority.getAuthority())
                );
    }

    /**
     * 대상 사용자를 지정하지 않으면 로그인 사용자를 사용한다.
     * 다른 사용자 지정은 관리자에게만 허용한다.
     */
    public Long resolveTargetUserId(
            CustomUserDetails principal,
            Long requestedUserId
    ) {
        Long loginUserId = requireLogin(principal);

        if (requestedUserId == null) {
            return loginUserId;
        }

        validateUserId(requestedUserId);

        if (Objects.equals(loginUserId, requestedUserId)
                || isAdmin(principal)) {
            return requestedUserId;
        }

        throw new ApiException(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "본인의 거래만 사용할 수 있습니다."
        );
    }

    /**
     * 요청값이 아닌 DB에서 조회한 거래 소유자로 검사한다.
     */
    public void requireOwnerOrAdmin(
            CustomUserDetails principal,
            TaxTransactionDto transaction
    ) {
        Long loginUserId = requireLogin(principal);

        if (transaction == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "TRANSACTION_NOT_FOUND",
                    "거래를 찾을 수 없습니다."
            );
        }

        if (isAdmin(principal)) {
            return;
        }

        if (!Objects.equals(
                loginUserId,
                transaction.getUserId()
        )) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ACCESS_DENIED",
                    "본인의 거래만 조회하거나 변경할 수 있습니다."
            );
        }
    }

    public void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw invalid(
                    "userId",
                    "유효하지 않은 사용자 번호입니다."
            );
        }
    }

    public void validateTransactionId(Long transactionId) {
        if (transactionId == null || transactionId <= 0) {
            throw invalid(
                    "transactionId",
                    "거래 번호를 확인해주세요."
            );
        }
    }

    public void validateTransactions(
            List<TaxTransactionDto> rows
    ) {
        if (rows == null || rows.isEmpty()) {
            throw invalid(
                    "rows",
                    "저장할 거래가 없습니다."
            );
        }

        for (int index = 0; index < rows.size(); index++) {
            validateTransaction(
                    rows.get(index),
                    "rows[" + index + "]"
            );
        }
    }

    public void validateTransaction(TaxTransactionDto row) {
        validateTransaction(row, "transaction");
    }

    private void validateTransaction(
            TaxTransactionDto row,
            String field
    ) {
        if (row == null) {
            throw invalid(
                    field,
                    "거래가 비어 있습니다."
            );
        }

        LocalDate date = row.getTransactionDate();

        if (date == null
                || date.getYear() < 1000
                || date.getYear() > 9999) {
            throw invalid(
                    field + ".transactionDate",
                    "유효한 거래 날짜가 필요합니다."
            );
        }

        BigDecimal amount = row.getAmount();

        if (amount == null
                || amount.signum() < 0
                || amount.compareTo(MAX_AMOUNT) > 0) {
            throw invalid(
                    field + ".amount",
                    "금액이 저장 가능한 범위를 벗어났습니다."
            );
        }

        try {
            row.setAmount(
                    amount.setScale(
                            2,
                            RoundingMode.UNNECESSARY
                    )
            );
        } catch (ArithmeticException e) {
            throw invalid(
                    field + ".amount",
                    "금액은 소수 둘째 자리까지만 입력할 수 있습니다."
            );
        }

        if (!"INCOME".equals(row.getTransactionType())
                && !"EXPENSE".equals(row.getTransactionType())) {
            throw invalid(
                    field + ".transactionType",
                    "INCOME 또는 EXPENSE를 선택해주세요."
            );
        }

        validateText(
                row.getDescription(),
                255,
                field + ".description",
                "거래 설명"
        );

        validateText(
                row.getCategory(),
                50,
                field + ".category",
                "거래 분류"
        );
    }

    private void validateText(
            String value,
            int maxLength,
            String field,
            String label
    ) {
        if (value == null
                || value.isBlank()
                || value.codePointCount(0, value.length()) > maxLength) {
            throw invalid(
                    field,
                    label + "은 1~" + maxLength + "자로 입력해주세요."
            );
        }
    }

    public byte[] validatePdf(
            MultipartFile file
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw invalid(
                    "file",
                    "PDF 파일이 비어 있습니다."
            );
        }

        String filename = file.getOriginalFilename();

        if (filename == null
                || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw invalid(
                    "file",
                    "PDF 파일만 업로드할 수 있습니다."
            );
        }

        byte[] bytes = file.getBytes();

        if (bytes.length < 5
                || bytes[0] != '%'
                || bytes[1] != 'P'
                || bytes[2] != 'D'
                || bytes[3] != 'F'
                || bytes[4] != '-') {
            throw invalid(
                    "file",
                    "올바른 PDF 파일이 아닙니다."
            );
        }

        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw invalid(
                        "file",
                        "암호화되지 않은 PDF를 업로드해주세요."
                );
            }

            if (document.getNumberOfPages() == 0) {
                throw invalid(
                        "file",
                        "PDF에 페이지가 없습니다."
                );
            }
        } catch (IOException e) {
            throw invalid(
                    "file",
                    "손상되었거나 열 수 없는 PDF 파일입니다."
            );
        }

        return bytes;
    }

    private ApiException invalid(
            String field,
            String message
    ) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                message,
                Map.of(field, message)
        );
    }
}