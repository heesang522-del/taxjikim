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
import java.util.regex.Pattern;

@Component
public class TaxValidator {

    // DECIMAL(15, 2)의 저장 범위다.
    private static final BigDecimal MAX_AMOUNT =
            new BigDecimal("9999999999999.99");

    // DB 조건에 따라 세율은 0 이상 100 이하를 허용한다.
    private static final BigDecimal MAX_RATE =
            new BigDecimal("100");

    private static final int MAX_UNSIGNED_SMALL_INT = 65535;
    private static final int MAX_DESCRIPTION_LENGTH = 255;

    // 카테고리 목록은 제한하지 않고 코드 형식과 길이만 검사한다.
    private static final Pattern CATEGORY_PATTERN =
            Pattern.compile("[A-Z][A-Z0-9_]{0,49}");

    // 현재 API에서 사용하는 사업장 지역 코드다.
    private static final Set<String> BUSINESS_REGIONS = Set.of(
            "CAPITAL_CONGESTION",
            "CAPITAL_OTHER",
            "NON_CAPITAL"
    );

    // 현재 API에서 사용하는 거래 구분이다.
    private static final Set<String> TRANSACTION_TYPES = Set.of(
            "INCOME",
            "EXPENSE",
            "UNKNOWN"
    );

    // 현재 API에서 사용하는 저장 상태다.
    private static final Set<String> SAVE_STATUSES = Set.of(
            "TEMP",
            "SAVED"
    );

    public void validateId(Long value, String field) {
        if (value == null || value <= 0) {
            throw invalid(
                    field,
                    "유효한 번호가 필요합니다."
            );
        }
    }

    public void validateTaxYear(Integer taxYear) {
        if (taxYear == null
                || taxYear < 1000
                || taxYear > 9999) {
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

        // 회원 분석은 분석 소유자에게만 허용한다.
        if (input.getUserId() != null
                && Objects.equals(
                input.getUserId(),
                access.getUserId()
        )) {
            return;
        }

        // 비회원 분석은 해당 분석을 생성한 서버 세션에서만 허용한다.
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
            throw invalid(
                    "input",
                    "분석 기본정보가 필요합니다."
            );
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

        if (region != null
                && !BUSINESS_REGIONS.contains(region)) {
            throw invalid(
                    "businessRegion",
                    "지원하지 않는 사업장 지역 코드입니다."
            );
        }

        String saveStatus = request.getSaveStatus();

        if (saveStatus == null
                || !SAVE_STATUSES.contains(saveStatus)) {
            throw invalid(
                    "saveStatus",
                    "저장 상태는 TEMP 또는 SAVED여야 합니다."
            );
        }

        /*
         * 기본정보의 기존 저장 검증만 수행한다.
         * 사업자 여부에 따른 분석 필수 조건은
         * 최종 분석을 수행하는 서버 경로에서 별도로 검사해야 한다.
         */
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

        String type = row.getTransactionType();

        // 거래 구분의 NULL 허용은 현재 DB 구조를 따른다.
        if (type != null
                && !TRANSACTION_TYPES.contains(type)) {
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
         * 거래 날짜 자체의 범위를 검사한다.
         * 거래 연도와 분석 귀속 연도의 일치 여부는
         * 서비스의 저장 경로에서 검사한다.
         */

        String description = row.getDescription();

        if (description != null
                && description.codePointCount(
                0,
                description.length()
        ) > MAX_DESCRIPTION_LENGTH) {
            throw invalid(
                    field + ".description",
                    "거래 설명은 255자 이하여야 합니다."
            );
        }

        /*
         * 검증 중에는 거래 값을 변경하지 않는다.
         * 빈 카테고리의 기본값 설정은 서비스의
         * copyStoredTransaction에서 수행한다.
         */
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
        validateCategory(
                category,
                "category"
        );
    }

    private void validateCategory(
            String category,
            String field
    ) {
        // 실제 카테고리 목록과 관계없이 코드 형식만 검사한다.
        if (category == null // 만약 카테고리가 없거나 , 정해진 코드 형식에 맞지 않으면 오류 발생
                || !CATEGORY_PATTERN.matcher(category).matches()) {
            throw invalid(
                    field,
                    "카테고리는 영문 대문자로 시작하는 50자 이하 코드여야 합니다."
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

            throw invalid(
                    field,
                    "필수 입력값입니다."
            );
        }

        // 실제 나이 제한이 아니라 DB 타입의 저장 범위다.
        if (value < 0 || value > MAX_UNSIGNED_SMALL_INT) {
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
        // 거래의 금액·세율 NULL 허용은 현재 DB 구조를 따른다.
        if (value == null) {
            return;
        }

        if (value.signum() < 0
                || value.compareTo(max) > 0) {
            throw invalid(
                    field,
                    "저장 가능한 숫자 범위를 벗어났습니다."
            );
        }

        try {
            // 값을 반올림하지 않고 허용 자릿수인지 확인한다.
            value.setScale(
                    scale,
                    RoundingMode.UNNECESSARY
            );
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

        // 확장자뿐 아니라 PDF 파일의 시작 표식도 검사한다.
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

        /*
         * 파일 크기는 서버의 multipart 설정에서 제한한다.
         * PDF 전체 구조와 페이지 검사는 후속 처리에서 수행한다.
         * 저장된 파일과의 중복 여부는 별도 DB 검사 대상이다.
         */
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