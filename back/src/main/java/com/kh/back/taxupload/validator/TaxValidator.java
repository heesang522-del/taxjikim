package com.kh.back.taxupload.validator;

import com.kh.back.common.exception.ApiException;
import com.kh.back.taxupload.dto.TaxTransactionDto;
import com.kh.back.taxupload.dto.TaxTransactionDto.AccessContext;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxInputRequest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class TaxValidator {

    private static final BigDecimal MAX_AMOUNT =
            new BigDecimal("9999999999999.99");

    private static final BigDecimal MAX_RATE =
            new BigDecimal("100");

    private static final Set<String> BUSINESS_REGIONS = Set.of(
            "CAPITAL_CONGESTION",
            "CAPITAL_OTHER",
            "NON_CAPITAL"
    );

    private static final Set<String> TRANSACTION_TYPES = Set.of(
            "INCOME",
            "EXPENSE",
            "UNKNOWN"
    );

    public void validateId(Long value, String field) {
        if (value == null || value <= 0) {
            throw invalid(field, "유효한 번호가 필요합니다.");
        }
    }

    public void validateTaxYear(Integer taxYear) {
        if (taxYear == null || taxYear < 1000 || taxYear > 9999) {
            throw invalid(
                    "taxYear",
                    "귀속 연도를 네 자리 숫자로 입력해주세요."
            );
        }
    }

    public Long requireLogin(AccessContext access) {
        if (access == null
                || access.getUserId() == null
                || access.getUserId() <= 0) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "로그인이 필요합니다."
            );
        }

        return access.getUserId();
    }

    public void requireAccess(
            AccessContext access,
            TaxInputRequest input
    ) {
        if (input == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "TAX_INPUT_NOT_FOUND",
                    "분석을 찾을 수 없습니다."
            );
        }

        if (access == null) {
            throw forbidden();
        }

        if (access.isAdmin()) {
            return;
        }

        if (input.getUserId() != null
                && Objects.equals(
                input.getUserId(),
                access.getUserId()
        )) {
            return;
        }

        // 비회원 분석은 해당 분석을 생성한 서버 세션에서만 접근한다.
        if (input.getUserId() == null
                && access.getGuestInputIds() != null
                && access.getGuestInputIds()
                .contains(input.getTaxInputId())) {
            return;
        }

        throw forbidden();
    }

    public void validateInput(TaxInputRequest request) {
        if (request == null) {
            throw invalid("input", "분석 기본정보가 필요합니다.");
        }

        validateTaxYear(request.getTaxYear());

        validateUnsignedSmallInt(
                request.getAge(),
                "age",
                true
        );

        validateUnsignedSmallInt(
                request.getDependentCount(),
                "dependentCount",
                false
        );

        if (request.getDonationAmount() == null) {
            throw invalid(
                    "donationAmount",
                    "기부금은 누락할 수 없습니다. 없으면 0을 입력해주세요."
            );
        }

        validateDecimal(
                request.getDonationAmount(),
                MAX_AMOUNT,
                2,
                "donationAmount"
        );

        String region = request.getBusinessRegion();

        if (region != null && !BUSINESS_REGIONS.contains(region)) {
            throw invalid(
                    "businessRegion",
                    "지원하지 않는 사업장 지역 코드입니다."
            );
        }

        String saveStatus = request.getSaveStatus();

        if (!"TEMP".equals(saveStatus)
                && !"SAVED".equals(saveStatus)) {
            throw invalid(
                    "saveStatus",
                    "저장 상태는 TEMP 또는 SAVED여야 합니다."
            );
        }
    }

    public void validateTransactions(
            List<TaxTransactionDto> rows
    ) {
        if (rows == null || rows.isEmpty()) {
            throw invalid("rows", "저장할 거래가 없습니다.");
        }

        for (int index = 0; index < rows.size(); index++) {
            validateTransaction(
                    rows.get(index),
                    "rows[" + index + "]"
            );
        }
    }

    private void validateTransaction(
            TaxTransactionDto row,
            String field
    ) {
        if (row == null) {
            throw invalid(field, "거래가 비어 있습니다.");
        }

        String type = row.getTransactionType();

        if (type != null && !TRANSACTION_TYPES.contains(type)) {
            throw invalid(
                    field + ".transactionType",
                    "INCOME, EXPENSE, UNKNOWN 중 하나여야 합니다."
            );
        }

        if (row.getTransactionDate() != null) {
            int year = row.getTransactionDate().getYear();

            if (year < 1000 || year > 9999) {
                throw invalid(
                        field + ".transactionDate",
                        "저장 가능한 거래 날짜가 아닙니다."
                );
            }
        }

        /*
         * 저장 단계에서는 거래 연도를 분석 연도와 강제로 맞추지 않는다.
         * 실제 계산 대상 거래의 범위는 계산 엔진에서 검증한다.
         */

        if (row.getDescription() != null
                && row.getDescription().codePointCount(
                0,
                row.getDescription().length()
        ) > 255) {
            throw invalid(
                    field + ".description",
                    "거래 설명은 255자 이하여야 합니다."
            );
        }

        if (row.getCategory() == null
                || row.getCategory().isBlank()) {
            row.setCategory("UNCLASSIFIED");
        }

        validateCategory(
                row.getCategory(),
                field + ".category"
        );

        validateDecimal(
                row.getAmount(),
                MAX_AMOUNT,
                2,
                field + ".amount"
        );

        validateDecimal(
                row.getPreTaxIncome(),
                MAX_AMOUNT,
                2,
                field + ".preTaxIncome"
        );

        validateDecimal(
                row.getDeductedTaxRate(),
                MAX_RATE,
                4,
                field + ".deductedTaxRate"
        );

        if ("EXPENSE".equals(type)
                && row.getPreTaxIncome() != null) {
            throw invalid(
                    field + ".preTaxIncome",
                    "지출 거래의 세전 수입은 NULL이어야 합니다."
            );
        }
    }

    public void validateCategory(String category) {
        validateCategory(category, "category");
    }

    private void validateCategory(
            String category,
            String field
    ) {
        // 실제 허용 카테고리 목록이 확정되면 목록 검증도 추가한다.
        if (category == null
                || !category.matches("[A-Z][A-Z0-9_]{0,49}")) {
            throw invalid(
                    field,
                    "카테고리는 영문 대문자로 시작하는 50자 이하 코드여야 합니다."
            );
        }
    }

    /*
     * DB 저장 검증과 역산 검증은 구분한다.
     * 실제로 세전 금액을 역산하는 계산 경로에서만 호출한다.
     */
    public void validateInverseRate(BigDecimal rate) {
        if (rate == null
                || rate.signum() < 0
                || rate.compareTo(MAX_RATE) >= 0) {
            throw invalid(
                    "deductedTaxRate",
                    "세전 금액 역산에는 0 이상 100 미만의 세율이 필요합니다."
            );
        }
    }

    private void validateUnsignedSmallInt(
            Integer value,
            String field,
            boolean nullable
    ) {
        if (value == null) {
            if (nullable) {
                return;
            }

            throw invalid(field, "필수 입력값입니다.");
        }

        if (value < 0 || value > 65535) {
            throw invalid(
                    field,
                    "0부터 65535 사이의 정수여야 합니다."
            );
        }
    }

    private void validateDecimal(
            BigDecimal value,
            BigDecimal max,
            int scale,
            String field
    ) {
        // 거래의 금액·세율 누락은 DB 설계에 따라 허용한다.
        if (value == null) {
            return;
        }

        if (value.signum() < 0 || value.compareTo(max) > 0) {
            throw invalid(field, "저장 가능한 숫자 범위를 벗어났습니다.");
        }

        try {
            // 저장 과정에서 금액이나 세율을 임의로 반올림하지 않는다.
            value.setScale(scale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw invalid(
                    field,
                    "소수점 이하 " + scale + "자리까지만 입력할 수 있습니다."
            );
        }
    }

    public byte[] validatePdf(
            MultipartFile file
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw invalid("file", "PDF 파일이 비어 있습니다.");
        }

        String filename = file.getOriginalFilename();

        if (filename == null
                || !filename.toLowerCase(Locale.ROOT)
                .endsWith(".pdf")) {
            throw invalid("file", "PDF 파일만 업로드할 수 있습니다.");
        }

        byte[] bytes = file.getBytes();

        if (bytes.length < 5
                || bytes[0] != '%'
                || bytes[1] != 'P'
                || bytes[2] != 'D'
                || bytes[3] != 'F'
                || bytes[4] != '-') {
            throw invalid("file", "올바른 PDF 파일이 아닙니다.");
        }

        return bytes;
    }

    private ApiException forbidden() {
        return new ApiException(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "해당 분석에 접근할 권한이 없습니다."
        );
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