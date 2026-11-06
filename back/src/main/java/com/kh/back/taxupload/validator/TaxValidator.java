package com.kh.back.taxupload.validator;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.exception.ApiException;
import com.kh.back.taxupload.dto.PdfParseResponseDto;
import com.kh.back.taxupload.dto.TaxTransactionDto;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxInputRequest;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxServiceRequest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
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
            new BigDecimal("9999999999999.99");

    // 실제 적용 세율이 아니라 기존 입력 검증 범위다.
    private static final BigDecimal MAX_RATE =
            new BigDecimal("100");

    private static final int MAX_UNSIGNED_SMALL_INT = 65535;
    private static final int MAX_DESCRIPTION_LENGTH = 255;

    private static final Pattern CATEGORY_PATTERN =
            Pattern.compile("[A-Z][A-Z0-9_]{0,49}");

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

    private static final Set<String> SAVE_STATUSES = Set.of(
            "TEMP",
            "SAVED"
    );

    public void validateRequest(
            TaxServiceRequest request,
            String message
    ) {
        if (request == null) {
            throw invalid("request", message);
        }
    }

    public void validateSaveRequest(TaxServiceRequest request) {
        boolean createAnalysis = request.getTaxInputId() == null;

        if (!createAnalysis && request.getInput() != null) {
            throw invalid(
                    "input",
                    "기존 분석의 기본정보는 수정 API를 이용해주세요."
            );
        }

        // 새 분석은 기본정보만 저장할 수 있지만 기존 분석은 거래 목록이 필요하다.
        if (!createAnalysis && request.getTransactions() == null) {
            throw invalid(
                    "transactions",
                    "저장할 거래 목록이 필요합니다."
            );
        }
    }

    public void validateUpdateRequest(
            Long taxInputId,
            TaxServiceRequest request
    ) {
        boolean updateProfile = request.getInput() != null;
        boolean updateCategory =
                request.getTransactionId() != null
                        || request.getCategory() != null;

        if (!updateProfile && !updateCategory) {
            throw invalid(
                    "request",
                    "수정할 기본정보 또는 카테고리를 전달해주세요."
            );
        }

        validateRequestTaxInputId(
                taxInputId,
                request.getTaxInputId()
        );

        if (updateCategory) {
            validateId(
                    request.getTransactionId(),
                    "transactionId"
            );
            validateCategory(request.getCategory());
        }
    }

    public void validateDeleteRequest(
            Long taxInputId,
            TaxServiceRequest request,
            boolean deleteAll
    ) {
        validateRequestTaxInputId(
                taxInputId,
                request.getTaxInputId()
        );

        if (deleteAll && request.getTransactionId() != null) {
            throw invalid(
                    "transactionId",
                    "분석 전체 삭제에는 거래번호를 전달할 수 없습니다."
            );
        }

        if (!deleteAll) {
            validateId(
                    request.getTransactionId(),
                    "transactionId"
            );
        }
    }

    public void validateRequestTaxInputId(
            Long pathTaxInputId,
            Long requestTaxInputId
    ) {
        if (requestTaxInputId != null
                && !Objects.equals(
                requestTaxInputId,
                pathTaxInputId
        )) {
            throw invalid(
                    "taxInputId",
                    "경로와 요청 본문의 분석번호가 다릅니다."
            );
        }
    }

    public void validateInputSource(TaxInputRequest source) {
        if (source == null) {
            throw invalid(
                    "input",
                    "분석 기본정보가 필요합니다."
            );
        }
    }

    public void validateInput(TaxInputRequest request) {
        // buildInput()에서 원본 존재 확인과 기본값 적용을 마친 DTO를 검사한다.
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

        // buildInput()에서 누락된 기부금은 0으로 채운다.
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

        if (saveStatus == null || !SAVE_STATUSES.contains(saveStatus)) {
            throw invalid(
                    "saveStatus",
                    "저장 상태는 TEMP 또는 SAVED여야 합니다."
            );
        }
    }

    public void validateInitialSaveAllowed(
            List<TaxTransactionDto> existing
    ) {
        if (!existing.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "USE_TRANSACTION_CHANGE_API",
                    "기존 거래는 수정·삭제 API로 변경해주세요."
            );
        }
    }

    public void validateGuestOwnership(
            Set<Long> guestInputIds,
            Long taxInputId
    ) {
        if (!guestInputIds.contains(taxInputId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ACCESS_DENIED",
                    "직접 생성한 비회원 분석만 저장할 수 있습니다."
            );
        }
    }

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

    public void requireAccess(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            TaxInputRequest input
    ) {
        if (input == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "TAX_INPUT_NOT_FOUND",
                    "분석을 찾을 수 없습니다."
            );
        }

        boolean admin = principal != null
                && principal.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(
                                authority.getAuthority()
                        )
                );

        if (admin) {
            return;
        }

        Long userId = principal == null
                ? null
                : principal.getUserId();

        if (input.getUserId() != null
                && Objects.equals(input.getUserId(), userId)) {
            return;
        }

        if (input.getUserId() == null
                && guestInputIds != null
                && guestInputIds.contains(input.getTaxInputId())) {
            return;
        }

        throw new ApiException(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "해당 분석에 접근할 권한이 없습니다."
        );
    }

    public void validateId(Long value, String field) {
        if (value == null || value <= 0) {
            throw invalid(
                    field,
                    "유효한 번호가 필요합니다."
            );
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

    public void validateSaveTransactionSource(
            TaxTransactionDto source,
            Long taxInputId
    ) {
        if (source == null) {
            throw invalid(
                    "rows",
                    "거래 목록에 빈 항목이 있습니다."
            );
        }

        if (isReviewMarked(source)) {
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

        validateCashTransaction(source);
    }

    public void validateSaveTransaction(
            TaxTransactionDto row,
            Integer taxYear
    ) {
        validateStoredTransaction(row);

        if (row.getTransactionDate() == null) {
            throw invalid(
                    "transactionDate",
                    "날짜별 거래 내역만 저장할 수 있습니다."
            );
        }

        int year = row.getTransactionDate().getYear();

        // 날짜 범위는 이 단계에서 한 번만 검사한다.
        validateTaxYear(year);

        if (!Objects.equals(taxYear, Integer.valueOf(year))) {
            throw invalid(
                    "transactionDate",
                    "거래 날짜의 연도가 분석 귀속 연도와 다릅니다."
            );
        }
    }

    public void validatePdfTransactionBeforeConversion(
            TaxTransactionDto source
    ) {
        validateCashTransaction(source);

        if (source.getTransactionDate() == null) {
            throw invalid(
                    "transactionDate",
                    "날짜별 상세 거래 내역이 필요합니다."
            );
        }

        // PDF 추출에는 특정 분석 귀속 연도와의 비교를 적용하지 않는다.
        validateTaxYear(source.getTransactionDate().getYear());
    }

    public void validatePdfTransactionAfterConversion(
            TaxTransactionDto row
    ) {
        validateStoredTransaction(row);

        // 단건 검증을 위해 임시 List를 만들지 않는다.
        validateTransactionFields(row, "rows[0]");
    }

    private void validateCashTransaction(TaxTransactionDto row) {
        if ("DEDUCTION_ELIGIBLE".equals(row.getAmountBasis())) {
            throw invalid(
                    "amount",
                    "공제대상금액은 실제 입출금액 컬럼에 저장할 수 없습니다."
            );
        }

        if ("VALIDATION_TOTAL".equals(row.getValueKind())) {
            throw invalid(
                    "valueKind",
                    "검산용 합계는 거래 목록에 저장할 수 없습니다."
            );
        }

        if ("GROSS".equals(row.getAmountBasis())
                && row.getActualAmount() == null) {
            throw invalid(
                    "actualAmount",
                    "세전 금액만 있는 거래는 실제 입금액 확인이 필요합니다."
            );
        }
    }

    private void validateStoredTransaction(TaxTransactionDto row) {
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

    public void validateTransactions(List<TaxTransactionDto> rows) {
        // 서비스에서 만든 목록만 전달받는다.
        // 원본 null과 날짜 검사는 변환 전후 단계에서 이미 끝난 상태다.
        for (int index = 0; index < rows.size(); index++) {
            validateTransactionFields(
                    rows.get(index),
                    "rows[" + index + "]"
            );
        }
    }

    private void validateTransactionFields(
            TaxTransactionDto row,
            String field
    ) {
        String type = row.getTransactionType();

        if (type != null && !TRANSACTION_TYPES.contains(type)) {
            throw invalid(
                    field + ".transactionType",
                    "INCOME, EXPENSE, UNKNOWN 중 하나여야 합니다."
            );
        }

        // 설명의 존재 여부는 먼저 검사했으므로 여기서는 길이만 검사한다.
        String description = row.getDescription();

        if (description.codePointCount(
                0,
                description.length()
        ) > MAX_DESCRIPTION_LENGTH) {
            throw invalid(
                    field + ".description",
                    "거래 설명은 255자 이하여야 합니다."
            );
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

        if ("EXPENSE".equals(type) && row.getPreTaxIncome() != null) {
            throw invalid(
                    field + ".preTaxIncome",
                    "지출 거래의 세전 수입은 NULL이어야 합니다."
            );
        }
    }

    public void validateNewTransactionIds(
            List<TaxTransactionDto> rows
    ) {
        for (TaxTransactionDto source : rows) {
            if (source.getTransactionId() != null) {
                throw invalid(
                        "transactionId",
                        "저장된 거래는 개별 수정·삭제 API를 이용해주세요."
                );
            }
        }
    }

    public void validateCategory(String category) {
        validateCategory(category, "category");
    }

    private void validateCategory(
            String category,
            String field
    ) {
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

            throw invalid(field, "필수 입력값입니다.");
        }

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
        // 선택 금액 필드는 null을 허용한다.
        // 필수 금액의 누락 검사는 해당 데이터의 검증 단계에서 수행한다.
        if (value == null) {
            return;
        }

        if (value.signum() < 0 || value.compareTo(max) > 0) {
            throw invalid(
                    field,
                    "저장 가능한 숫자 범위를 벗어났습니다."
            );
        }

        try {
            value.setScale(scale, RoundingMode.UNNECESSARY);

        } catch (ArithmeticException exception) {
            throw invalid(
                    field,
                    "소수점 이하 " + scale + "자리까지만 입력할 수 있습니다."
            );
        }
    }

    public void validateCategoryUpdateResult(int updated) {
        validateSingleRowResult(
                updated,
                "TRANSACTION_UPDATE_FAILED",
                "거래 수정 결과를 확인하지 못했습니다."
        );
    }

    public void validateInputDeleteResult(int deleted) {
        validateSingleRowResult(
                deleted,
                "TAX_INPUT_DELETE_FAILED",
                "분석 삭제 결과를 확인하지 못했습니다."
        );
    }

    public void validateTransactionDeleteResult(int deleted) {
        validateSingleRowResult(
                deleted,
                "TRANSACTION_DELETE_FAILED",
                "거래 삭제 결과를 확인하지 못했습니다."
        );
    }

    private void validateSingleRowResult(
            int affectedRows,
            String code,
            String message
    ) {
        if (affectedRows != 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    code,
                    message
            );
        }
    }

    public void validateTransactionFound(
            List<TaxTransactionDto> rows
    ) {
        if (rows.isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "TRANSACTION_NOT_FOUND",
                    "해당 분석의 거래를 찾을 수 없습니다."
            );
        }
    }

    public void validatePdfResponse(PdfParseResponseDto response) {
        if (response == null
                || response.getTransactions() == null
                || response.getTransactions().isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PDF_TRANSACTIONS_NOT_FOUND",
                    "PDF에서 거래 내역을 찾지 못했습니다."
            );
        }
    }

    public boolean isReviewMarked(TaxTransactionDto row) {
        return Boolean.TRUE.equals(row.getNeedsReview())
                || Boolean.TRUE.equals(row.getExcluded());
    }

    public boolean requiresPdfReview(TaxTransactionDto row) {
        return !Boolean.TRUE.equals(row.getParseSuccess())
                || isReviewMarked(row);
    }

    public byte[] validatePdf(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw invalid("file", "PDF 파일이 비어 있습니다.");
        }

        String filename = file.getOriginalFilename();

        if (filename == null
                || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
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

    public void validateOcrSettings(
            int dpi,
            int maxPages,
            long maxRenderedPixels
    ) {
        if (dpi <= 0 || maxPages <= 0 || maxRenderedPixels <= 0) {
            throw new IllegalStateException(
                    "OCR 처리 한도 설정을 확인해주세요."
            );
        }
    }

    public void validateOcrPdfBytes(byte[] pdfBytes) {
        // OCR 서비스가 독립적으로 호출되는 경우에도 입력을 보호한다.
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw invalid("file", "PDF 데이터가 없습니다.");
        }
    }

    public void validateOcrLanguageData(
            String tessdataPath,
            String language
    ) {
        if (tessdataPath == null
                || tessdataPath.isBlank()
                || language == null
                || language.isBlank()) {
            throw ocrDataError();
        }

        Path directory = Path.of(tessdataPath);

        if (!Files.isDirectory(directory)) {
            throw ocrDataError();
        }

        for (String item : language.split("\\+")) {
            String code = item.trim();

            if (!code.matches("[A-Za-z0-9_]+")
                    || !Files.isReadable(
                    directory.resolve(code + ".traineddata")
            )) {
                throw ocrDataError();
            }
        }
    }

    public void validateOcrDocument(
            boolean encrypted,
            int pageCount,
            int maxPages
    ) {
        if (encrypted) {
            throw invalid(
                    "file",
                    "암호화되지 않은 PDF를 업로드해주세요."
            );
        }

        if (pageCount == 0) {
            throw invalid("file", "PDF에 페이지가 없습니다.");
        }

        if (pageCount > maxPages) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PDF_PAGE_LIMIT_EXCEEDED",
                    "PDF 페이지 수가 허용 범위를 초과했습니다."
            );
        }
    }

    public void validateOcrImageSize(
            double widthPoints,
            double heightPoints,
            int dpi,
            long maxRenderedPixels
    ) {
        double scale = dpi / 72.0;

        long width = (long) Math.ceil(widthPoints * scale);
        long height = (long) Math.ceil(heightPoints * scale);

        // 곱셈 오버플로 없이 렌더링 픽셀 제한을 확인한다.
        if (width <= 0
                || height <= 0
                || width > maxRenderedPixels / height) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PDF_IMAGE_LIMIT_EXCEEDED",
                    "PDF 이미지 크기가 허용 범위를 초과했습니다."
            );
        }
    }

    public boolean sameTransactions(
            List<TaxTransactionDto> existing,
            List<TaxTransactionDto> requested
    ) {
        if (existing.size() != requested.size()) {
            return false;
        }

        for (int index = 0; index < existing.size(); index++) {
            TaxTransactionDto left = existing.get(index);
            TaxTransactionDto right = requested.get(index);

            if (!Objects.equals(
                    left.getTransactionType(),
                    right.getTransactionType()
            ) || !Objects.equals(
                    left.getTransactionDate(),
                    right.getTransactionDate()
            ) || !Objects.equals(
                    left.getDescription(),
                    right.getDescription()
            ) || !Objects.equals(
                    left.getCategory(),
                    right.getCategory()
            ) || !sameDecimal(
                    left.getAmount(),
                    right.getAmount()
            ) || !sameDecimal(
                    left.getPreTaxIncome(),
                    right.getPreTaxIncome()
            ) || !sameDecimal(
                    left.getDeductedTaxRate(),
                    right.getDeductedTaxRate()
            )) {
                return false;
            }
        }

        return true;
    }

    private boolean sameDecimal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }

        return left.compareTo(right) == 0;
    }

    private ApiException ocrDataError() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "OCR_DATA_NOT_CONFIGURED",
                "OCR 언어 데이터 경로 또는 언어 파일을 확인해주세요."
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