package com.kh.back.taxupload.validator;

import com.kh.back.common.exception.ApiException;
import com.kh.back.common.security.JwtTokenProvider;
import com.kh.back.taxupload.dto.TaxCategoryChange;
import com.kh.back.taxupload.dto.TaxTransactionDto;
import com.kh.back.taxupload.dto.TaxTransactionDto.AccessContext;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxInputRequest;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxServiceRequest;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class TaxValidator {

    public static final String GUEST_TAX_TOKEN_TYPE = "guest-tax-access";

    // DECIMAL(15, 2)의 DB 저장 범위다.
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.99");

    // 세율 필드에 저장할 수 있는 퍼센트 값의 상한이다.
    private static final BigDecimal MAX_RATE = new BigDecimal("100");
    private static final BigDecimal PDF_AMOUNT_TOLERANCE = BigDecimal.ONE;
    private static final int MAX_UNSIGNED_SMALL_INT = 65535;
    private static final int MAX_DESCRIPTION_LENGTH = 255;

    private static final Set<String> INCOME_CATEGORIES = Set.of(
            "YOUTUBE_SETTLEMENT", "AD_SPONSORSHIP", "EXTERNAL_SUPPORT"
    );

    private static final Set<String> EXPENSE_CATEGORIES = Set.of(
            "SOFTWARE_LICENSE", "EQUIPMENT", "TELECOMMUNICATION", "TRANSPORT",
            "SUPPLIES_PROPS", "SPACE_RENTAL", "OUTSOURCING", "AD_PROMOTION"
    );

    private static final Set<String> TRANSACTION_TYPES = Set.of(
            "INCOME", "EXPENSE", "UNKNOWN"
    );

    private static final Set<String> SAVE_STATUSES = Set.of("TEMP", "SAVED");

    private final JwtTokenProvider jwtTokenProvider;

    public void validateId(Long value, String field) {
        if (value == null || value <= 0) {
            throw invalid(field, "유효한 번호가 필요합니다.");
        }
    }

    public void validateTaxYear(Integer taxYear) {
        if (taxYear == null || taxYear < 1000 || taxYear > 9999) {
            throw invalid("taxYear", "귀속 연도를 네 자리 숫자로 입력해주세요.");
        }
    }

    public Long readGuestTaxInputId(String guestAccessToken) {
        if (guestAccessToken == null || guestAccessToken.isBlank()) {
            return null;
        }

        try {
            // 서명과 만료 시각을 검증한 뒤 비회원 분석번호를 꺼낸다.
            Claims claims = Jwts.parser()
                    .verifyWith(jwtTokenProvider.getSecretKey())
                    .build()
                    .parseSignedClaims(guestAccessToken)
                    .getPayload();

            if (!GUEST_TAX_TOKEN_TYPE.equals(claims.get("type"))
                    || claims.getExpiration() == null) {
                return null;
            }

            Long taxInputId = claims.get("taxInputId", Long.class);
            return taxInputId != null && taxInputId > 0 ? taxInputId : null;
        } catch (JwtException | IllegalArgumentException exception) {
            // 검증에 실패하면 비회원 분석 권한을 부여하지 않는다.
            return null;
        }
    }

    public void requireAccess(AccessContext access, TaxInputRequest input) {
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

        // 회원 분석은 로그인한 소유자에게 허용한다.
        if (input.getUserId() != null
                && Objects.equals(input.getUserId(), access.getUserId())) {
            return;
        }

        // 비회원 분석은 검증된 토큰이 가리키는 분석 한 건에만 허용한다.
        if (input.getUserId() == null
                && access.getGuestTaxInputId() != null
                && Objects.equals(input.getTaxInputId(), access.getGuestTaxInputId())) {
            return;
        }

        throw forbidden();
    }

    public void validateInputPresent(TaxInputRequest input) {
        if (input == null) {
            throw invalid("input", "분석 기본정보가 필요합니다.");
        }
    }

    public void validateInput(TaxInputRequest request) {
        validateInputPresent(request);

        validateTaxYear(request.getTaxYear());
        validateUnsignedSmallInt(request.getDependentCount(), "dependentCount");

        if (request.getGeneralDonationAmount() == null
                || request.getSpecialDonationAmount() == null) {
            throw invalid(
                    request.getGeneralDonationAmount() == null
                            ? "generalDonationAmount"
                            : "specialDonationAmount",
                    "기부금은 누락할 수 없습니다. 없으면 0을 입력해주세요."
            );
        }

        validateDecimal(
                request.getGeneralDonationAmount(),
                MAX_AMOUNT,
                2,
                "generalDonationAmount"
        );
        validateDecimal(
                request.getSpecialDonationAmount(),
                MAX_AMOUNT,
                2,
                "specialDonationAmount"
        );

        String saveStatus = request.getSaveStatus();

        if (saveStatus == null || !SAVE_STATUSES.contains(saveStatus)) {
            throw invalid("saveStatus", "저장 상태는 TEMP 또는 SAVED여야 합니다.");
        }
    }

    public void validateRequestPresent(TaxServiceRequest request, String message) {
        if (request == null) {
            throw invalid("request", message);
        }
    }

    public void validateMatchingTaxInputId(Long pathId, Long bodyId) {
        if (bodyId != null && !Objects.equals(pathId, bodyId)) {
            throw invalid("taxInputId", "경로와 요청 본문의 분석번호가 다릅니다.");
        }
    }

    public void validateExistingAnalysisSave(
            TaxInputRequest input,
            List<TaxTransactionDto> rows
    ) {
        // 기존 분석의 기본정보는 별도 수정 요청으로 처리한다.
        if (input != null) {
            throw invalid("input", "기존 분석의 기본정보는 수정 API를 이용해주세요.");
        }

        if (rows == null) {
            throw invalid("transactions", "저장할 거래 목록이 필요합니다.");
        }
    }

    public void validateUpdateTargets(boolean updateProfile, boolean updateCategory) {
        if (!updateProfile && !updateCategory) {
            throw invalid("request", "수정할 기본정보 또는 카테고리를 전달해주세요.");
        }
    }

    public void validateDeleteTargets(
            boolean deleteAll,
            Long transactionId,
            List<Long> transactionIds
    ) {
        // 목록 삭제는 전체 삭제나 단건 삭제와 함께 요청할 수 없다.
        if (transactionIds != null) {
            if (deleteAll || transactionId != null || transactionIds.isEmpty()) {
                throw invalid("transactionIds", "삭제할 거래번호 목록만 전달해주세요.");
            }

            for (Long id : transactionIds) {
                validateId(id, "transactionIds");
            }

            return;
        }

        if (deleteAll) {
            if (transactionId != null) {
                throw invalid(
                        "transactionId",
                        "분석 전체 삭제에는 거래번호를 전달할 수 없습니다."
                );
            }

            return;
        }

        validateId(transactionId, "transactionId");
    }

    public void validateCategoryChangeTarget(
            TaxCategoryChange change,
            Set<Long> seenTransactionIds
    ) {
        if (change == null) {
            throw invalid("changes", "변경할 거래는 한 번씩 전달해주세요.");
        }

        Long transactionId = change.transactionId();
        validateId(transactionId, "transactionId");

        // 같은 거래는 한 요청에서 한 번만 수정할 수 있다.
        if (!seenTransactionIds.add(transactionId)) {
            throw invalid("changes", "변경할 거래는 한 번씩 전달해주세요.");
        }
    }

    public void validateTransactionSource(TaxTransactionDto source, Long taxInputId) {
        if (source == null) {
            throw invalid("rows", "거래 목록에 빈 항목이 있습니다.");
        }

        // 검토 중이거나 제외된 거래는 저장하지 않는다.
        if (Boolean.TRUE.equals(source.getNeedsReview())
                || Boolean.TRUE.equals(source.getExcluded())) {
            throw invalid("rows", "검토 대상 또는 제외한 거래는 저장할 수 없습니다.");
        }

        if (source.getTaxInputId() != null
                && !Objects.equals(source.getTaxInputId(), taxInputId)) {
            throw invalid("taxInputId", "거래의 분석번호가 저장 대상과 다릅니다.");
        }
    }

    public void validateCashTransaction(TaxTransactionDto row) {
        // 공제대상금액은 실제 입출금액과 구분한다.
        if ("DEDUCTION_ELIGIBLE".equals(row.getAmountBasis())) {
            throw invalid("amount", "공제대상금액은 실제 입출금액 컬럼에 저장할 수 없습니다.");
        }

        // 합계를 개별 거래와 함께 저장해 중복 집계하는 것을 막는다.
        if ("VALIDATION_TOTAL".equals(row.getValueKind())) {
            throw invalid("valueKind", "검산용 합계는 거래 목록에 저장할 수 없습니다.");
        }

        // 세전 금액만으로 실제 입금액을 확정하지 않는다.
        if ("GROSS".equals(row.getAmountBasis()) && row.getActualAmount() == null) {
            throw invalid(
                    "actualAmount",
                    "세전 금액만 있는 거래는 실제 입금액 확인이 필요합니다."
            );
        }
    }

    public void validateStoredTransaction(TaxTransactionDto row) {
        validateDescriptionPresent(row);

        if (row.getAmount() == null) {
            throw invalid("amount", "실제 입출금액이 필요합니다.");
        }
    }

    public void validateStoredTransactionDate(TaxTransactionDto row, Integer taxYear) {
        if (row.getTransactionDate() == null) {
            throw invalid("transactionDate", "날짜별 거래 내역만 저장할 수 있습니다.");
        }

        int year = row.getTransactionDate().getYear();
        validateTaxYear(year);

        // 저장할 분석의 귀속 연도와 거래 날짜의 연도를 비교한다.
        if (!Objects.equals(taxYear, Integer.valueOf(year))) {
            throw invalid("transactionDate", "거래 날짜의 연도가 분석 귀속 연도와 다릅니다.");
        }
    }

    public void validatePdfTransactionDate(TaxTransactionDto source) {
        if (source.getTransactionDate() == null) {
            throw invalid("transactionDate", "날짜별 상세 거래 내역이 필요합니다.");
        }

        // PDF 추출 단계에서는 거래 날짜의 연도 범위를 확인한다.
        validateTaxYear(source.getTransactionDate().getYear());
    }

    public void validateExtractedPdfTransaction(
            TaxTransactionDto source,
            TaxTransactionDto normalized
    ) {
        validateCashTransaction(source);
        validatePdfTransactionDate(source);
        validateStoredTransaction(normalized);
        validateTransaction(normalized, "transactions");

        String type = normalized.getTransactionType();

        if (!"INCOME".equals(type) && !"EXPENSE".equals(type)) {
            throw invalid("transactionType", "수입 및 지출 구분 확인 필요");
        }

        BigDecimal gross = source.getPreTaxIncome();
        BigDecimal tax = source.getTaxDeducted();
        validateDecimal(tax, MAX_AMOUNT, 2, "taxDeducted");

        if (!"INCOME".equals(type) || gross == null) {
            return;
        }

        // 파서가 추출한 값을 바탕으로 저장 전에 금액의 일관성을 확인한다.
        BigDecimal difference = gross.subtract(normalized.getAmount());

        if (difference.signum() < 0) {
            throw invalid("actualAmount", "실제 입금액이 세전 금액보다 큽니다.");
        }

        if (tax != null && tax.compareTo(difference) != 0) {
            throw invalid("taxDeducted", "세액과 세전·실제 금액의 차이가 다릅니다.");
        }

        BigDecimal rate = source.getDeductedTaxRate();

        if (rate != null) {
            BigDecimal expected = gross.multiply(rate).movePointLeft(2);

            if (difference.subtract(expected).abs().compareTo(PDF_AMOUNT_TOLERANCE) > 0) {
                throw invalid("deductedTaxRate", "세율과 실제 금액이 일치하지 않습니다.");
            }
        }
    }

    public void validateNewTransactionIds(List<TaxTransactionDto> rows) {
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

    public void validateTransactions(List<TaxTransactionDto> rows) {
        if (rows == null || rows.isEmpty()) {
            throw invalid("rows", "저장할 거래가 없습니다.");
        }

        for (int index = 0; index < rows.size(); index++) {
            validateTransaction(rows.get(index), "rows[" + index + "]");
        }
    }

    private void validateTransaction(TaxTransactionDto row, String field) {
        if (row == null) {
            throw invalid(field, "거래가 비어 있습니다.");
        }

        String type = row.getTransactionType();

        // 거래 구분의 null 허용은 기존 DB 구조를 따른다.
        if (type != null && !TRANSACTION_TYPES.contains(type)) {
            throw invalid(
                    field + ".transactionType",
                    "INCOME, EXPENSE, UNKNOWN 중 하나여야 합니다."
            );
        }

        String description = row.getDescription();

        if (description != null
                && description.codePointCount(0, description.length()) > MAX_DESCRIPTION_LENGTH) {
            throw invalid(field + ".description", "거래 설명은 255자 이하여야 합니다.");
        }

        String category = row.getCategory();

        if (!"UNCLASSIFIED".equals(category) && !isAllowedCategory(category, type)) {
            throw invalid(field + ".category", "거래 구분에 맞는 카테고리를 선택해주세요.");
        }

        validateDecimal(row.getAmount(), MAX_AMOUNT, 2, field + ".amount");
        validateDecimal(row.getPreTaxIncome(), MAX_AMOUNT, 2, field + ".preTaxIncome");
        validateDecimal(row.getDeductedTaxRate(), MAX_RATE, 4, field + ".deductedTaxRate");

        if ("EXPENSE".equals(type) && row.getPreTaxIncome() != null) {
            throw invalid(field + ".preTaxIncome", "지출 거래의 세전 수입은 NULL이어야 합니다.");
        }
    }

    public void validateCategory(String category, TaxTransactionDto row) {
        if ("UNCLASSIFIED".equals(category)) {
            throw invalid("category", "미분류는 수정할 카테고리로 선택할 수 없습니다.");
        }

        validateDescriptionPresent(row);

        String transactionType = row.getTransactionType();

        if (!"INCOME".equals(transactionType) && !"EXPENSE".equals(transactionType)) {
            throw invalid(
                    "transactionType",
                    "수입 또는 지출로 구분된 거래만 수정할 수 있습니다."
            );
        }

        if (!isAllowedCategory(category, transactionType)) {
            throw invalid("category", "거래 구분에 맞는 카테고리를 선택해주세요.");
        }
    }

    private void validateDescriptionPresent(TaxTransactionDto row) {
        String description = row.getDescription();

        if (description == null || description.isBlank()) {
            throw invalid("description", "거래 설명이 필요합니다.");
        }
    }

    private boolean isAllowedCategory(String category, String transactionType) {
        if (category == null) {
            return false;
        }

        return "OTHER".equals(category)
                || ("INCOME".equals(transactionType) && INCOME_CATEGORIES.contains(category))
                || ("EXPENSE".equals(transactionType) && EXPENSE_CATEGORIES.contains(category));
    }

    private void validateUnsignedSmallInt(Integer value, String field) {
        if (value == null) {
            throw invalid(field, "필수 입력값입니다.");
        }

        // 가족 수를 저장할 DB 타입의 범위를 확인한다.
        if (value < 0 || value > MAX_UNSIGNED_SMALL_INT) {
            throw invalid(field, "0부터 65535 사이의 정수여야 합니다.");
        }
    }

    private void validateDecimal(BigDecimal value, BigDecimal max, int scale, String field) {
        // null 필수 여부는 별도 검사에서 판단한다.
        if (value == null) {
            return;
        }

        if (value.signum() < 0 || value.compareTo(max) > 0) {
            throw invalid(field, "저장 가능한 숫자 범위를 벗어났습니다.");
        }

        try {
            // 값을 반올림하지 않고 허용 소수 자릿수인지 확인한다.
            value.setScale(scale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw invalid(field, "소수점 이하 " + scale + "자리까지만 입력할 수 있습니다.");
        }
    }

    public byte[] validatePdf(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw invalid("file", "PDF 파일이 비어 있습니다.");
        }

        String filename = file.getOriginalFilename();

        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw invalid("file", "PDF 파일만 업로드할 수 있습니다.");
        }

        byte[] bytes = file.getBytes();

        // 확장자와 함께 파일 시작 표식도 확인한다.
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

    private ApiException invalid(String field, String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                message,
                Map.of(field, message)
        );
    }
}