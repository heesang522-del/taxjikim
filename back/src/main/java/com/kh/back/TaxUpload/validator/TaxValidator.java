package com.kh.back.TaxUpload.validator;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.exception.ApiException;
import com.kh.back.TaxUpload.dto.TaxTransactionDto;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    // DB의 DECIMAL(15,2) 저장 범위다.
    private static final BigDecimal MAX_AMOUNT =
            new BigDecimal("9999999999999.99");

    /**
     * 로그인 아이디가 아닌 실제 회원 PK를 반환한다.
     */
    public Long requireLogin() {

        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                instanceof CustomUserDetails details)) {

            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "로그인이 필요합니다."
            );
        }

        Long userId = details.getUserId();

        if (userId == null || userId <= 0) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "회원 인증 정보를 확인할 수 없습니다."
            );
        }

        return userId;
    }

    /**
     * 서버에서 인증한 관리자 권한을 검사한다.
     */
    public boolean isAdmin() {

        requireLogin();

        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(
                                authority.getAuthority()
                        )
                );
    }

    /**
     * 대상 사용자 번호를 확정한다.
     *
     * 일반 사용자: 본인만 가능
     * 관리자: 다른 사용자 지정 가능
     * 지정하지 않으면 로그인한 사용자
     */
    public Long resolveTargetUserId(Long requestedUserId) {

        Long loginUserId = requireLogin();

        if (requestedUserId == null) {
            return loginUserId;
        }

        validateUserId(requestedUserId);

        if (isAdmin()) {
            return requestedUserId;
        }

        if (!Objects.equals(loginUserId, requestedUserId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ACCESS_DENIED",
                    "본인의 거래만 사용할 수 있습니다."
            );
        }

        return loginUserId;
    }

    /**
     * 기존 거래는 DB에서 조회한 실제 소유자로 검사한다.
     */
    public void requireOwnerOrAdmin(TaxTransactionDto transaction) {

        Long loginUserId = requireLogin();

        if (transaction == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "TRANSACTION_NOT_FOUND",
                    "거래를 찾을 수 없습니다."
            );
        }

        if (isAdmin()) {
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
            throw invalid(field, "거래가 비어 있습니다.");
        }

        LocalDate date = row.getTransactionDate();

        // MySQL DATE 컬럼의 지원 범위에 맞춰 검사한다.
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
            // 반올림으로 금액이 달라지지 않도록 초과 소수 자릿수를 거부한다.
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
                || value.codePointCount(0, value.length())
                > maxLength) {

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
                || !filename.toLowerCase(Locale.ROOT)
                .endsWith(".pdf")) {

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