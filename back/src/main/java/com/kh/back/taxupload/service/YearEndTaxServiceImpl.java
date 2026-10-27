package com.kh.back.taxupload.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.kh.back.common.exception.ApiException;
import com.kh.back.taxupload.dto.PageOcrResultDto;
import com.kh.back.taxupload.dto.PdfParseResponseDto;
import com.kh.back.taxupload.dto.TaxTransactionDto;
import com.kh.back.taxupload.dto.TaxTransactionDto.AccessContext;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxInputRequest;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxResultResponse;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxResultRow;
import com.kh.back.taxupload.mapper.TaxMapper;
import com.kh.back.taxupload.validator.TaxValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class YearEndTaxServiceImpl implements YearEndTaxService {

    private final TaxValidator taxValidator;
    private final TaxMapper taxMapper;
    private final TaxOcrService taxOcrService;
    private final PythonPdfClient pythonPdfClient;
    private final PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public TaxInputRequest createInput(
            AccessContext access,
            TaxInputRequest request
    ) {
        TaxInputRequest input =
                normalizeInput(request, null, userId(access));

        taxValidator.validateInput(input);

        if ("SAVED".equals(input.getSaveStatus())) {
            input.setUserId(taxValidator.requireLogin(access));
        }

        taxMapper.insertInput(input);

        return input;
    }

    @Override
    @Transactional(readOnly = true)
    public TaxInputRequest getInput(
            AccessContext access,
            Long taxInputId
    ) {
        return requireInput(access, taxInputId, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxInputRequest> getSavedInputs(
            AccessContext access,
            Integer taxYear
    ) {
        Long loginUserId = taxValidator.requireLogin(access);

        if (taxYear != null) {
            taxValidator.validateTaxYear(taxYear);
        }

        return taxMapper.findSavedInputs(loginUserId, taxYear);
    }

    @Override
    @Transactional
    public TaxInputRequest updateInput(
            AccessContext access,
            Long taxInputId,
            TaxInputRequest request
    ) {
        TaxInputRequest existing =
                requireInput(access, taxInputId, true);

        TaxInputRequest updated = normalizeInput(
                request,
                taxInputId,
                existing.getUserId()
        );

        taxValidator.validateInput(updated);

        if (existing.getUserId() == null
                && "SAVED".equals(updated.getSaveStatus())) {

            Long loginUserId = taxValidator.requireLogin(access);

            if (access.getGuestInputIds() == null
                    || !access.getGuestInputIds().contains(taxInputId)) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "ACCESS_DENIED",
                        "직접 생성한 비회원 분석만 저장할 수 있습니다."
                );
            }

            updated.setUserId(loginUserId);
        }

        taxMapper.updateInput(updated);
        taxMapper.deleteResult(taxInputId);

        return updated;
    }

    @Override
    @Transactional
    public void deleteInput(
            AccessContext access,
            Long taxInputId
    ) {
        requireInput(access, taxInputId, true);
        taxMapper.deleteInput(taxInputId);
    }

    /**
     * 미저장 거래의 최초 저장에만 사용한다.
     * 기존 거래의 수정·삭제는 개별 API로 처리한다.
     */
    @Override
    @Transactional
    public List<TaxTransactionDto> saveTransactions(
            AccessContext access,
            Long taxInputId,
            List<TaxTransactionDto> rows
    ) {
        TaxInputRequest input =
                requireInput(access, taxInputId, true);

        if (rows == null) {
            throw invalid(
                    "rows",
                    "저장할 거래 목록이 필요합니다."
            );
        }

        List<TaxTransactionDto> requested = new ArrayList<>();

        for (TaxTransactionDto source : rows) {
            if (source == null) {
                throw invalid(
                        "rows",
                        "거래 목록에 빈 항목이 있습니다."
                );
            }

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

            requireCashTransaction(source);

            TaxTransactionDto row =
                    copyStoredTransaction(source, taxInputId);

            if (source.getActualAmount() != null) {
                row.setAmount(source.getActualAmount());
            }

            validateStoredTransaction(row);

            if (row.getTransactionDate() == null) {
                throw invalid(
                        "transactionDate",
                        "날짜별 거래 내역만 저장할 수 있습니다."
                );
            }

            int year = row.getTransactionDate().getYear();

            taxValidator.validateTaxYear(year);

            if (!Objects.equals(
                    input.getTaxYear(),
                    Integer.valueOf(year)
            )) {
                throw invalid(
                        "transactionDate",
                        "거래 날짜의 연도가 분석 귀속 연도와 다릅니다."
                );
            }

            requested.add(row);
        }

        if (!requested.isEmpty()) {
            taxValidator.validateTransactions(requested);
        }

        List<TaxTransactionDto> existing =
                taxMapper.findTransactions(taxInputId);

        // 같은 내용의 재요청은 기존 거래번호를 유지한다.
        if (sameTransactions(existing, requested)) {
            return existing;
        }

        // 기존 전체 거래를 삭제하거나 다시 저장하지 않는다.
        if (!existing.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "USE_TRANSACTION_CHANGE_API",
                    "기존 거래는 개별 수정·삭제 API로 변경해주세요."
            );
        }

        for (TaxTransactionDto source : rows) {
            if (source.getTransactionId() != null) {
                throw invalid(
                        "transactionId",
                        "저장된 거래는 개별 수정·삭제 API를 이용해주세요."
                );
            }
        }

        for (TaxTransactionDto row : requested) {
            taxMapper.insertTransaction(row);
        }

        if (!requested.isEmpty()) {
            invalidateAfterTransactionChange(taxInputId);
        }

        return taxMapper.findTransactions(taxInputId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxTransactionDto> getTransactions(
            AccessContext access,
            Long taxInputId
    ) {
        requireInput(access, taxInputId, false);

        return taxMapper.findTransactions(taxInputId);
    }

    /**
     * 프론트에서 전달한 카테고리만 해당 거래에 반영한다.
     * 신규 분석 생성, 전체 거래 저장, 변경 이력 저장은 하지 않는다.
     */
    @Override
    @Transactional
    public void updateCategory(
            AccessContext access,
            Long taxInputId,
            Long transactionId,
            String category
    ) {
        requireInput(access, taxInputId, true);

        taxValidator.validateCategory(category);

        TaxTransactionDto existing =
                requireTransaction(taxInputId, transactionId);

        // 실제 변경이 없으면 UPDATE도 실행하지 않는다.
        if (Objects.equals(existing.getCategory(), category)) {
            return;
        }

        int updated = taxMapper.updateCategory(
                taxInputId,
                transactionId,
                category
        );

        if (updated != 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "TRANSACTION_UPDATE_FAILED",
                    "거래 수정 결과를 확인하지 못했습니다."
            );
        }

        invalidateAfterTransactionChange(taxInputId);
    }

    /**
     * 프론트에서 지정한 거래 한 건만 삭제한다.
     */
    @Override
    @Transactional
    public void deleteTransaction(
            AccessContext access,
            Long taxInputId,
            Long transactionId
    ) {
        requireInput(access, taxInputId, true);
        requireTransaction(taxInputId, transactionId);

        int deleted = taxMapper.deleteTransaction(
                taxInputId,
                transactionId
        );

        if (deleted != 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "TRANSACTION_DELETE_FAILED",
                    "거래 삭제 결과를 확인하지 못했습니다."
            );
        }

        invalidateAfterTransactionChange(taxInputId);
    }

    @Override
    @Transactional(readOnly = true)
    public TaxResultResponse getResult(
            AccessContext access,
            Long taxInputId
    ) {
        requireInput(access, taxInputId, false);

        TaxResultRow row = taxMapper.findResult(taxInputId);

        if (row == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "TAX_RESULT_NOT_FOUND",
                    "저장된 계산 결과가 없습니다."
            );
        }

        try {
            Map<String, Object> detail = objectMapper.readValue(
                    row.getResultDetail(),
                    new TypeReference<Map<String, Object>>() {}
            );

            String calculatedAt = row.getCalculatedAt() == null
                    ? null
                    : row.getCalculatedAt()
                    .atOffset(ZoneOffset.ofHours(9))
                    .toString();

            return TaxResultResponse.builder()
                    .taxInputId(row.getTaxInputId())
                    .resultDetail(detail)
                    .calculatedAt(calculatedAt)
                    .build();

        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "INVALID_STORED_RESULT",
                    "저장된 계산 결과를 읽을 수 없습니다."
            );
        }
    }

    @Override
    public TaxResultResponse calculateInput(
            AccessContext access,
            Long taxInputId
    ) {
        requireInput(access, taxInputId, false);

        ApiException exception = new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "CALCULATOR_NOT_CONFIGURED",
                "세금 계산 엔진이 아직 연결되지 않았습니다."
        );

        writeProcessLog(
                taxInputId,
                userId(access),
                "CALC",
                "FAIL",
                exception.getCode()
        );

        throw exception;
    }

    /**
     * PDF 검증·OCR·파싱만 수행한다.
     * DB 저장은 별도 최초 저장 요청에서 수행한다.
     */
    @Override
    public PdfParseResponseDto parsePdf(
            AccessContext access,
            Long taxInputId,
            MultipartFile file
    ) throws IOException {
        if (taxInputId != null) {
            requireInput(access, taxInputId, false);
        }

        try {
            byte[] bytes = taxValidator.validatePdf(file);

            List<PageOcrResultDto> pages =
                    taxOcrService.extractPages(bytes);

            PdfParseResponseDto response = pythonPdfClient.analyze(
                    bytes,
                    file.getOriginalFilename(),
                    pages
            );

            if (response == null
                    || response.getTransactions() == null
                    || response.getTransactions().isEmpty()) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "PDF_TRANSACTIONS_NOT_FOUND",
                        "PDF에서 거래 내역을 찾지 못했습니다."
                );
            }

            List<TaxTransactionDto> transactions =
                    preparePdfTransactions(
                            response.getTransactions()
                    );

            long reviewCount = transactions.stream()
                    .filter(row ->
                            Boolean.TRUE.equals(row.getNeedsReview())
                                    || Boolean.TRUE.equals(row.getExcluded())
                    )
                    .count();

            response.setTransactions(transactions);
            response.setSavedCount(0);
            response.setPersisted(false);
            response.setTaxInputIdsByYear(new LinkedHashMap<>());

            log.info(
                    "PDF 파싱 완료. 거래={}건, 검토={}건, DB 저장 없음",
                    transactions.size(),
                    reviewCount
            );

            return response;

        } catch (IOException | RuntimeException exception) {
            log.warn(
                    "PDF 파싱 실패. 코드={}",
                    failureCode(exception)
            );

            throw exception;
        }
    }

    private List<TaxTransactionDto> preparePdfTransactions(
            List<TaxTransactionDto> sources
    ) {
        List<TaxTransactionDto> result = new ArrayList<>();

        for (TaxTransactionDto source : sources) {
            if (source == null) {
                throw invalid(
                        "transactions",
                        "PDF 분석 결과에 빈 거래가 있습니다."
                );
            }

            source.setTaxInputId(null);
            source.setTransactionId(null);

            if (!Boolean.TRUE.equals(source.getParseSuccess())
                    || Boolean.TRUE.equals(source.getNeedsReview())
                    || Boolean.TRUE.equals(source.getExcluded())) {

                String reason = source.getFailureReason();

                markPdfReview(
                        source,
                        reason == null || reason.isBlank()
                                ? "추출한 거래 내용을 확인해주세요."
                                : reason
                );

                result.add(source);
                continue;
            }

            TaxTransactionDto row;

            try {
                requireCashTransaction(source);

                if (source.getTransactionDate() == null) {
                    throw invalid(
                            "transactionDate",
                            "날짜별 상세 거래 내역이 필요합니다."
                    );
                }

                taxValidator.validateTaxYear(
                        source.getTransactionDate().getYear()
                );

                row = copyStoredTransaction(source, null);

                if (source.getActualAmount() != null) {
                    row.setAmount(source.getActualAmount());
                }

                validateStoredTransaction(row);
                taxValidator.validateTransactions(List.of(row));

            } catch (ApiException exception) {
                String reason = exception.getMessage();

                markPdfReview(
                        source,
                        reason == null || reason.isBlank()
                                ? "거래 내용을 확인해주세요."
                                : reason
                );

                result.add(source);
                continue;
            }

            row.setActualAmount(row.getAmount());
            row.setPageNumber(source.getPageNumber());
            row.setRawText(source.getRawText());
            row.setValueKind(source.getValueKind());
            row.setAmountBasis(source.getAmountBasis());
            row.setTaxDeducted(source.getTaxDeducted());
            row.setDocumentYear(source.getDocumentYear());

            row.setParseSuccess(true);
            row.setNeedsReview(false);
            row.setExcluded(false);
            row.setFailureReason(null);

            result.add(row);
        }

        return result;
    }

    private void markPdfReview(
            TaxTransactionDto row,
            String reason
    ) {
        row.setNeedsReview(true);
        row.setExcluded(true);

        String current = row.getFailureReason();

        if (current == null || current.isBlank()) {
            row.setFailureReason(reason);
            return;
        }

        if (current.equals(reason)) {
            return;
        }

        for (String existing : current.split(" / ")) {
            if (existing.equals(reason)) {
                return;
            }
        }

        row.setFailureReason(current + " / " + reason);
    }

    private TaxTransactionDto copyStoredTransaction(
            TaxTransactionDto source,
            Long taxInputId
    ) {
        String type = source.getTransactionType();

        if (type == null || type.isBlank()) {
            type = "UNKNOWN";
        }

        String category = source.getCategory();

        if (category == null || category.isBlank()) {
            category = "UNCLASSIFIED";
        }

        return TaxTransactionDto.builder()
                .taxInputId(taxInputId)
                .transactionType(type)
                .transactionDate(source.getTransactionDate())
                .description(source.getDescription())
                .category(category)
                .preTaxIncome(source.getPreTaxIncome())
                .deductedTaxRate(source.getDeductedTaxRate())
                .amount(source.getAmount())
                .build();
    }

    private void requireCashTransaction(TaxTransactionDto row) {
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

    private TaxInputRequest normalizeInput(
            TaxInputRequest source,
            Long taxInputId,
            Long ownerId
    ) {
        if (source == null) {
            throw invalid(
                    "input",
                    "분석 기본정보가 필요합니다."
            );
        }

        return TaxInputRequest.builder()
                .taxInputId(taxInputId)
                .userId(ownerId)
                .taxYear(source.getTaxYear())
                .businessRegistered(source.getBusinessRegistered())
                .age(source.getAge())
                .startupReliefApplied(source.getStartupReliefApplied())
                .businessRegion(source.getBusinessRegion())
                .dependentCount(
                        source.getDependentCount() == null
                                ? 0
                                : source.getDependentCount()
                )
                .donationAmount(
                        source.getDonationAmount() == null
                                ? BigDecimal.ZERO
                                : source.getDonationAmount()
                )
                .saveStatus(
                        source.getSaveStatus() == null
                                ? "TEMP"
                                : source.getSaveStatus()
                )
                .build();
    }

    private TaxInputRequest requireInput(
            AccessContext access,
            Long taxInputId,
            boolean lock
    ) {
        taxValidator.validateId(taxInputId, "taxInputId");

        TaxInputRequest input = lock
                ? taxMapper.lockInput(taxInputId)
                : taxMapper.findInput(taxInputId);

        taxValidator.requireAccess(access, input);

        return input;
    }

    private TaxTransactionDto requireTransaction(
            Long taxInputId,
            Long transactionId
    ) {
        taxValidator.validateId(
                transactionId,
                "transactionId"
        );

        TaxTransactionDto row = taxMapper.findTransaction(
                taxInputId,
                transactionId
        );

        if (row == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "TRANSACTION_NOT_FOUND",
                    "해당 분석의 거래를 찾을 수 없습니다."
            );
        }

        return row;
    }

    private void invalidateAfterTransactionChange(
            Long taxInputId
    ) {
        taxMapper.deleteResult(taxInputId);
        taxMapper.touchInput(taxInputId);
    }

    private boolean sameTransactions(
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

    private boolean sameDecimal(
            BigDecimal left,
            BigDecimal right
    ) {
        if (left == null || right == null) {
            return left == right;
        }

        return left.compareTo(right) == 0;
    }

    private Long userId(AccessContext access) {
        return access == null ? null : access.getUserId();
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

    private String failureCode(Exception exception) {
        if (exception instanceof ApiException apiException) {
            return apiException.getCode();
        }

        return exception instanceof IOException
                ? "PDF_IO_ERROR"
                : "PDF_PROCESSING_FAILED";
    }

    // 기존 계산 처리 로그이며 거래 변경 이력과는 별개다.
    private void writeProcessLog(
            Long taxInputId,
            Long userId,
            String processType,
            String status,
            String failReason
    ) {
        TransactionTemplate template =
                new TransactionTemplate(transactionManager);

        template.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );

        try {
            template.executeWithoutResult(transactionStatus ->
                    taxMapper.insertProcessLog(
                            taxInputId,
                            userId,
                            processType,
                            status,
                            failReason
                    )
            );

        } catch (RuntimeException exception) {
            log.error(
                    "처리 이력 저장 실패. 유형={}, 상태={}, 예외={}",
                    processType,
                    status,
                    exception.getClass().getName()
            );
        }
    }
}