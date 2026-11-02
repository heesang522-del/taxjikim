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

    // 실제 금융 한도가 아니라 DECIMAL(15, 2)의 DB 저장 범위다.
    private static final BigDecimal MAX_AMOUNT =
            new BigDecimal("9999999999999.99");  // 상수 검사 기준을 모아둠

    // 실제 적용 세율이 아니라 DB에 저장할 수 있는 세율 범위다.
    private static final BigDecimal MAX_RATE =
            new BigDecimal("100"); //  db의 숫자 저장 한계

    private static final int MAX_UNSIGNED_SMALL_INT = 65535;  // 세율 데이터 허용 상한
    private static final int MAX_DESCRIPTION_LENGTH = 255;

    // 카테고리 목록 자체는 제한하지 않고 코드 형식과 길이만 검사한다.
    private static final Pattern CATEGORY_PATTERN =
            Pattern.compile("[A-Z][A-Z0-9_]{0,49}"); // 영문 대문자,숫자 , 전체길이 1~50 자

    private static final Set<String> BUSINESS_REGIONS = Set.of(
            "CAPITAL_CONGESTION",
            "CAPITAL_OTHER",       // 카테고리의 실제 의미나 목록까지 검사는 하지 않고 형식만 맞으면 통과
            "NON_CAPITAL"
    );

    private static final Set<String> TRANSACTION_TYPES = Set.of(
            "INCOME",
            "EXPENSE",
            "UNKNOWN"
    );

    private static final Set<String> SAVE_STATUSES = Set.of(
            "TEMP",
            "SAVED"
    );

    public void validateId(Long value, String field) {  // validateid() 번호검사
        if (value == null || value <= 0) {  // 분석번호나 거래번호가 유효한지 검사
            throw invalid(
                    field,
                    "유효한 번호가 필요합니다."
            );
        }
    }

    public void validateTaxYear(Integer taxYear) {  // 네자리 연도 검사
        if (taxYear == null
                || taxYear < 1000
                || taxYear > 9999) {
            throw invalid(
                    "taxYear",
                    "귀속 연도를 네 자리 숫자로 입력해주세요."
            );
        }
    }

    public Long requireLogin(AccessContext access) {  // 로그인 여부와 회원번호 확인
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

        // 회원 분석은 분석 소유자에게 허용한다.
        if (input.getUserId() != null
                && Objects.equals(
                input.getUserId(),
                access.getUserId()
        )) {
            return;
        }

        // 비회원 분석은 해당 분석을 생성한 서버 세션에만 허용한다.
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
         * 기본정보의 저장 검증만 수행한다.
         * 최종 분석에 필요한 별도 업무 조건은 계산 경로에서 검사한다.
         */
    }

    public void validateTransactionRows(
            List<TaxTransactionDto> rows
    ) {
        // 기존 동작을 유지해 빈 목록은 허용하고 null만 거절한다.
        if (rows == null) {
            throw invalid(
                    "rows",
                    "저장할 거래 목록이 필요합니다."
            );
        }
    }

    public void validateTransactionSource(
            TaxTransactionDto source,
            Long taxInputId
    ) {
        if (source == null) {
            throw invalid(
                    "rows",
                    "거래 목록에 빈 항목이 있습니다."
            );
        }

        // 검토 중이거나 제외된 거래는 저장하지 않는다.
        if (Boolean.TRUE.equals(source.getNeedsReview())
                || Boolean.TRUE.equals(source.getExcluded())) {
            throw invalid(
                    "rows",
                    "검토 대상 또는 제외한 거래는 저장할 수 없습니다."
            );
        }

        if (source.getTaxInputId() != null
                && !Objects.equals(
                source.getTaxInputId(),
                taxInputId
        )) {
            throw invalid(
                    "taxInputId",
                    "거래의 분석번호가 저장 대상과 다릅니다."
            );
        }
    }

    public void validateCashTransaction(TaxTransactionDto row) {
        // 공제대상금액은 실제 입출금액과 구분한다.
        if ("DEDUCTION_ELIGIBLE".equals(row.getAmountBasis())) {
            throw invalid(
                    "amount",
                    "공제대상금액은 실제 입출금액 컬럼에 저장할 수 없습니다."
            );
        }

        // 합계를 개별 거래와 함께 저장해 중복 집계하는 것을 막는다.
        if ("VALIDATION_TOTAL".equals(row.getValueKind())) {
            throw invalid(
                    "valueKind",
                    "검산용 합계는 거래 목록에 저장할 수 없습니다."
            );
        }

        // 세전 금액만으로 실제 입금액을 확정하지 않는다.
        if ("GROSS".equals(row.getAmountBasis())
                && row.getActualAmount() == null) {
            throw invalid(
                    "actualAmount",
                    "세전 금액만 있는 거래는 실제 입금액 확인이 필요합니다."
            );
        }
    }

    public void validateStoredTransaction(TaxTransactionDto row) {
        if (row.getDescription() == null
                || row.getDescription().isBlank()) {
            throw invalid(
                    "description",
                    "거래 설명이 필요합니다."
            );
        }

        if (row.getAmount() == null) {
            throw invalid(
                    "amount",
                    "실제 입출금액이 필요합니다."
            );
        }
    }

    public void validateStoredTransactionDate(
            TaxTransactionDto row,
            Integer taxYear
    ) {
        if (row.getTransactionDate() == null) {
            throw invalid(
                    "transactionDate",
                    "날짜별 거래 내역만 저장할 수 있습니다."
            );
        }

        int year = row.getTransactionDate().getYear();

        validateTaxYear(year);

        // 저장할 분석의 귀속 연도와 거래 날짜의 연도를 비교한다.
        if (!Objects.equals(taxYear, Integer.valueOf(year))) {
            throw invalid(
                    "transactionDate",
                    "거래 날짜의 연도가 분석 귀속 연도와 다릅니다."
            );
        }
    }

    public void validatePdfTransactionDate(TaxTransactionDto source) {
        if (source.getTransactionDate() == null) {
            throw invalid(
                    "transactionDate",
                    "날짜별 상세 거래 내역이 필요합니다."
            );
        }

        // PDF 추출 단계에서는 특정 분석 연도와 비교하지 않는다.
        validateTaxYear(source.getTransactionDate().getYear());
    }

    public void validateNewTransactionIds(
            List<TaxTransactionDto> rows
    ) {
        // 최초 INSERT 대상에는 기존 거래번호를 받지 않는다.
        for (TaxTransactionDto source : rows) {
            if (source.getTransactionId() != null) {
                throw invalid(
                        "transactionId",
                        "저장된 거래는 개별 수정·삭제 API를 이용해주세요."
                );
            }
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

        // 거래 구분의 null 허용은 기존 DB 구조를 따른다.
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
         * 여기서는 날짜 자체의 범위만 검사한다.
         * 분석 연도와의 일치는 validateStoredTransactionDate에서 검사한다.
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

        // 검증 중에는 값을 바꾸지 않고 전달된 값만 검사한다.
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
        // 실제 분류 목록이 아니라 코드의 형식과 길이를 검사한다.
        if (category == null
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

        // 실제 나이 제한 등이 아니라 DB 타입의 저장 범위다.
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
        // null 필수 여부는 별도 검사에서 판단한다.
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
            // 값을 반올림하지 않고 허용 소수 자릿수인지 확인한다.
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

        // 확장자와 함께 파일 시작 표식도 확인한다.
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
         * 파일 크기는 multipart 설정에서 제한한다.
         * PDF 전체 구조와 페이지 검사는 후속 처리에서 수행한다.
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