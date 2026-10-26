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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
        TaxInputRequest input = normalizeInput(
                request,
                null,
                userId(access)
        );

        taxValidator.validateInput(input);

        if ("SAVED".equals(input.getSaveStatus())) {
            input.setUserId(
                    taxValidator.requireLogin(access)
            );
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

        return taxMapper.findSavedInputs(
                loginUserId,
                taxYear
        );
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

            Long loginUserId =
                    taxValidator.requireLogin(access);

            if (access.getGuestInputIds() == null
                    || !access.getGuestInputIds()
                    .contains(taxInputId)) {

                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "ACCESS_DENIED",
                        "직접 생성한 비회원 분석만 저장할 수 있습니다."
                );
            }

            updated.setUserId(loginUserId);
        }

        taxMapper.updateInput(updated);

        // 기본정보가 달라졌으므로 이전 계산 결과를 무효화한다.
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

    @Override
    @Transactional
    public List<TaxTransactionDto> saveTransactions(
            AccessContext access,
            Long taxInputId,
            List<TaxTransactionDto> rows
    ) {
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

            // 실제 입출금액과 다른 자료가 저장 API로 들어오는 것을 막는다.
            requireCashTransaction(source);

            TaxTransactionDto row =
                    copyStoredTransaction(source, taxInputId);

            validateStoredTransaction(row);

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

        /*
         * 전달한 분석번호의 전체 목록을 교체한다.
         * 원본을 보존하는 수정본은 새 분석번호를 만든 뒤 저장한다.
         */
        taxMapper.deleteTransactions(taxInputId);

        for (TaxTransactionDto row : requested) {
            taxMapper.insertTransaction(row);
        }

        invalidateAfterTransactionChange(taxInputId);

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

    @Override
    @Transactional
    public void updateCategory(
            AccessContext access,
            Long taxInputId,
            Long transactionId,
            String category
    ) {
        requireInput(access, taxInputId, true);

        TaxTransactionDto existing =
                requireTransaction(taxInputId, transactionId);

        taxValidator.validateCategory(category);

        if (Objects.equals(
                existing.getCategory(),
                category
        )) {
            return;
        }

        if (existing.getCategory() != null
                && !existing.getCategory().isBlank()
                && !"UNCLASSIFIED".equals(existing.getCategory())) {

            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CATEGORY_ALREADY_CLASSIFIED",
                    "미분류 거래의 카테고리만 수정할 수 있습니다."
            );
        }

        taxMapper.updateCategory(
                taxInputId,
                transactionId,
                category
        );

        invalidateAfterTransactionChange(taxInputId);
    }

    @Override
    @Transactional
    public void deleteTransaction(
            AccessContext access,
            Long taxInputId,
            Long transactionId
    ) {
        requireInput(access, taxInputId, true);
        requireTransaction(taxInputId, transactionId);

        taxMapper.deleteTransaction(
                taxInputId,
                transactionId
        );

        invalidateAfterTransactionChange(taxInputId);
    }

    @Override
    @Transactional(readOnly = true)
    public TaxResultResponse getResult(
            AccessContext access,
            Long taxInputId
    ) {
        requireInput(access, taxInputId, false);

        TaxResultRow row =
                taxMapper.findResult(taxInputId);

        if (row == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "TAX_RESULT_NOT_FOUND",
                    "저장된 계산 결과가 없습니다."
            );
        }

        try {
            Map<String, Object> detail =
                    objectMapper.readValue(
                            row.getResultDetail(),
                            new TypeReference<Map<String, Object>>() {
                            }
                    );

            // 기존 DB의 계산 시각을 한국 시각으로 해석해 반환한다.
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

        // 실제 계산 엔진 연결 전에는 임의의 세액을 생성하지 않는다.
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

    @Override
    public PdfParseResponseDto parsePdf(
            AccessContext access,
            Long taxInputId,
            MultipartFile file
    ) throws IOException {

        TaxInputRequest baseInput = taxInputId == null
                ? null
                : requireInput(access, taxInputId, false);

        try {
            byte[] bytes =
                    taxValidator.validatePdf(file);

            /*
             * OCR과 Python 분석은 DB 트랜잭션 밖에서 처리한다.
             * 분석이 끝난 뒤 DB 저장 구간만 트랜잭션으로 묶는다.
             */
            List<PageOcrResultDto> pages =
                    taxOcrService.extractPages(bytes);

            PdfParseResponseDto response =
                    pythonPdfClient.analyze(
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

            Map<Integer, List<TaxTransactionDto>> groups =
                    preparePdfTransactions(
                            response.getTransactions(),
                            baseInput
                    );

            if (groups.isEmpty()) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "PDF_CASH_TRANSACTIONS_NOT_FOUND",
                        "현재 테이블에 저장할 실제 수입·지출 내역이 없습니다."
                );
            }

            TransactionTemplate template =
                    new TransactionTemplate(transactionManager);

            template.setPropagationBehavior(
                    TransactionDefinition.PROPAGATION_REQUIRES_NEW
            );

            // 이번 업로드의 모든 분석번호와 거래를 함께 저장한다.
            template.executeWithoutResult(status ->
                    savePdfSnapshot(
                            access,
                            baseInput,
                            response,
                            groups
                    )
            );

            log.info(
                    "PDF DB 저장 완료. savedCount={}, inputCount={}",
                    response.getSavedCount(),
                    response.getTaxInputIdsByYear().size()
            );

            for (Long createdId
                    : response.getTaxInputIdsByYear().values()) {

                writeProcessLog(
                        createdId,
                        userId(access),
                        "UPLOAD",
                        "SUCCESS",
                        null
                );
            }

            return response;

        } catch (IOException | RuntimeException exception) {
            writeProcessLog(
                    taxInputId,
                    userId(access),
                    "UPLOAD",
                    "FAIL",
                    failureCode(exception)
            );

            throw exception;
        }
    }

    private Map<Integer, List<TaxTransactionDto>> preparePdfTransactions(
            List<TaxTransactionDto> sources,
            TaxInputRequest baseInput
    ) {
        Map<Integer, List<TaxTransactionDto>> groups =
                new LinkedHashMap<>();

        Set<Integer> knownYears = new LinkedHashSet<>();

        // 날짜 없는 실제 거래의 분석 연도 확인에 사용할 근거를 수집한다.
        for (TaxTransactionDto row : sources) {
            if (row == null) {
                continue;
            }

            if (row.getTransactionDate() != null) {
                knownYears.add(
                        row.getTransactionDate().getYear()
                );

            } else if (row.getDocumentYear() != null) {
                knownYears.add(row.getDocumentYear());
            }
        }

        Integer fallbackYear = baseInput != null
                ? baseInput.getTaxYear()
                : knownYears.size() == 1
                ? knownYears.iterator().next()
                : null;

        for (TaxTransactionDto source : sources) {
            if (source == null) {
                throw invalid(
                        "transactions",
                        "PDF 분석 결과에 빈 거래가 있습니다."
                );
            }

            /*
             * 현재 AMOUNT는 실제 입출금액이다.
             * 공제대상금액과 검산용 합계는 여기에 섞어 넣지 않는다.
             */
            if ("DEDUCTION_ELIGIBLE".equals(source.getAmountBasis())
                    || "VALIDATION_TOTAL".equals(source.getValueKind())) {
                continue;
            }

            if (!Boolean.TRUE.equals(source.getParseSuccess())
                    || Boolean.TRUE.equals(source.getNeedsReview())) {

                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "PDF_REVIEW_REQUIRED",
                        "금액·항목·기간 확인이 필요한 거래가 있습니다."
                );
            }

            if (Boolean.TRUE.equals(source.getExcluded())) {
                continue;
            }

            requireCashTransaction(source);

            Integer year = source.getTransactionDate() != null
                    ? source.getTransactionDate().getYear()
                    : source.getDocumentYear() != null
                    ? source.getDocumentYear()
                    : fallbackYear;

            if (year == null) {
                throw invalid(
                        "taxYear",
                        "날짜 없는 거래의 분석 연도를 확인해주세요."
                );
            }

            taxValidator.validateTaxYear(year);

            TaxTransactionDto row =
                    copyStoredTransaction(source, null);

            // 실제 입금액이 따로 제공되면 해당 금액을 우선 저장한다.
            if (source.getActualAmount() != null) {
                row.setAmount(source.getActualAmount());
            }

            validateStoredTransaction(row);

            /*
             * 다음 필드는 DB 컬럼에 넣지 않는다.
             * 분석 응답에 원래 정보를 연결하기 위해 메모리에만 보관한다.
             */
            row.setPageNumber(source.getPageNumber());
            row.setRawText(source.getRawText());
            row.setTransactionMonth(source.getTransactionMonth());
            row.setDocumentYear(source.getDocumentYear());
            row.setValueKind(source.getValueKind());
            row.setAmountBasis(source.getAmountBasis());
            row.setTaxDeducted(source.getTaxDeducted());

            groups.computeIfAbsent(
                    year,
                    ignored -> new ArrayList<>()
            ).add(row);
        }

        for (List<TaxTransactionDto> rows : groups.values()) {
            taxValidator.validateTransactions(rows);
        }

        return groups;
    }

    private void savePdfSnapshot(
            AccessContext access,
            TaxInputRequest baseInput,
            PdfParseResponseDto response,
            Map<Integer, List<TaxTransactionDto>> groups
    ) {
        Map<Integer, Long> inputIds =
                new LinkedHashMap<>();

        List<TaxTransactionDto> savedRows =
                new ArrayList<>();

        for (Map.Entry<Integer, List<TaxTransactionDto>> entry
                : groups.entrySet()) {

            TaxInputRequest sourceInput = baseInput == null
                    ? TaxInputRequest.builder().build()
                    : baseInput;

            TaxInputRequest input = normalizeInput(
                    sourceInput,
                    null,
                    userId(access)
            );

            input.setTaxYear(entry.getKey());

            // TEMP도 실제 DB 저장이며 비회원 사용을 허용한다.
            input.setSaveStatus("TEMP");

            taxValidator.validateInput(input);
            taxMapper.insertInput(input);

            Long createdId = input.getTaxInputId();

            if (createdId == null) {
                throw saveFailure(
                        "분석번호를 생성하지 못했습니다."
                );
            }

            inputIds.put(entry.getKey(), createdId);

            Map<Long, TaxTransactionDto> sourceById =
                    new LinkedHashMap<>();

            for (TaxTransactionDto source : entry.getValue()) {
                TaxTransactionDto inserted =
                        copyStoredTransaction(source, createdId);

                taxMapper.insertTransaction(inserted);

                if (inserted.getTransactionId() == null) {
                    throw saveFailure(
                            "거래번호를 생성하지 못했습니다."
                    );
                }

                sourceById.put(
                        inserted.getTransactionId(),
                        source
                );
            }

            // 화면에는 DB에서 실제로 다시 조회한 값을 전달한다.
            List<TaxTransactionDto> queried =
                    taxMapper.findTransactions(createdId);

            if (queried.size() != sourceById.size()) {
                throw saveFailure(
                        "거래 저장 건수와 조회 건수가 다릅니다."
                );
            }

            for (TaxTransactionDto row : queried) {
                TaxTransactionDto source =
                        sourceById.get(row.getTransactionId());

                if (source == null) {
                    throw saveFailure(
                            "저장한 거래를 조회 결과에서 찾지 못했습니다."
                    );
                }

                row.setActualAmount(row.getAmount());

                // 분석용 필드는 응답에만 복원한다.
                row.setPageNumber(source.getPageNumber());
                row.setRawText(source.getRawText());
                row.setTransactionMonth(source.getTransactionMonth());
                row.setDocumentYear(source.getDocumentYear());
                row.setValueKind(source.getValueKind());
                row.setAmountBasis(source.getAmountBasis());
                row.setTaxDeducted(source.getTaxDeducted());

                row.setParseSuccess(true);
                row.setNeedsReview(false);
                row.setExcluded(false);
                row.setFailureReason(null);

                savedRows.add(row);
            }
        }

        response.setTransactions(savedRows);
        response.setTaxInputIdsByYear(inputIds);
        response.setSavedCount(savedRows.size());
        response.setPersisted(true);
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

        /*
         * 실제 DB 컬럼에 대응하는 필드만 복사한다.
         * 이전 transactionId는 복사하지 않아 새 거래번호를 생성한다.
         */
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

    private void requireCashTransaction(
            TaxTransactionDto row
    ) {
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

    private void validateStoredTransaction(
            TaxTransactionDto row
    ) {
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
        taxValidator.validateId(
                taxInputId,
                "taxInputId"
        );

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

        TaxTransactionDto row =
                taxMapper.findTransaction(
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

            /*
             * DB에 저장되는 컬럼만 비교한다.
             * DB에 없는 월·자료 종류 등을 비교해 항상 변경으로
             * 판단하던 문제를 방지한다.
             */
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
        return access == null
                ? null
                : access.getUserId();
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

    private ApiException saveFailure(
            String message
    ) {
        return new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "PDF_SAVE_FAILED",
                message
        );
    }

    private String failureCode(
            Exception exception
    ) {
        if (exception instanceof ApiException apiException) {
            return apiException.getCode();
        }

        return exception instanceof IOException
                ? "PDF_IO_ERROR"
                : "PDF_PROCESSING_FAILED";
    }

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
            // 로그 기록 실패가 완료된 거래 저장을 실패로 바꾸지 않게 한다.
            log.error(
                    "처리 이력 저장 실패. type={}, status={}, exceptionType={}",
                    processType,
                    status,
                    exception.getClass().getName()
            );
        }
    }
}