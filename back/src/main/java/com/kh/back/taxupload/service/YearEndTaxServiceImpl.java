package com.kh.back.taxupload.service;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.exception.ApiException;
import com.kh.back.taxupload.dto.PageOcrResultDto;
import com.kh.back.taxupload.dto.PdfParseResponseDto;
import com.kh.back.taxupload.dto.TaxServiceResponse;
import com.kh.back.taxupload.dto.TaxTransactionDto;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxInputRequest;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxServiceRequest;
import com.kh.back.taxupload.mapper.TaxMapper;
import com.kh.back.taxupload.validator.TaxValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    @Override
    @Transactional(readOnly = true)
    public TaxServiceResponse getTaxData(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            Long taxInputId
    ) {
        Set<Long> ownedGuestInputIds =
                copyGuestInputIds(guestInputIds);

        TaxInputRequest input = requireInput(
                principal,
                ownedGuestInputIds,
                taxInputId,
                false
        );

        List<TaxTransactionDto> transactions =
                taxMapper.findTransactions(taxInputId, null);

        return TaxServiceResponse.builder()
                .input(input)
                .transactions(transactions)
                .build();
    }

    @Override
    @Transactional
    public TaxServiceResponse save(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            TaxServiceRequest request
    ) {
        taxValidator.validateRequest(
                request,
                "저장 요청이 필요합니다."
        );

        Set<Long> ownedGuestInputIds =
                copyGuestInputIds(guestInputIds);

        Long taxInputId = request.getTaxInputId();
        List<TaxTransactionDto> rows = request.getTransactions();
        boolean createAnalysis = taxInputId == null;

        taxValidator.validateSaveRequest(request);

        TaxInputRequest input;

        if (createAnalysis) {
            // 새 분석의 소유자는 인증 정보로 결정한다.
            Long userId = principal == null
                    ? null
                    : principal.getUserId();

            input = buildInput(
                    request.getInput(),
                    null,
                    userId
            );

            taxValidator.validateInput(input);

            if ("SAVED".equals(input.getSaveStatus())) {
                Long loginUserId =
                        taxValidator.requireLogin(principal);

                input = buildInput(input, null, loginUserId);
            }

            taxMapper.insertInput(input);
            taxInputId = input.getTaxInputId();

        } else {
            input = requireInput(
                    principal,
                    ownedGuestInputIds,
                    taxInputId,
                    true
            );
        }

        List<TaxTransactionDto> savedRows = List.of();

        if (rows != null) {
            List<TaxTransactionDto> requested = new ArrayList<>();

            for (TaxTransactionDto source : rows) {
                taxValidator.validateSaveTransactionSource(
                        source,
                        taxInputId
                );

                TaxTransactionDto row = buildStoredTransaction(
                        source,
                        taxInputId
                );

                taxValidator.validateSaveTransaction(
                        row,
                        input.getTaxYear()
                );

                requested.add(row);
            }

            // 空 목록은 순회하지 않으므로 별도 빈 목록 검사가 필요 없다.
            // 기본 필드 검사는 기존과 동일하게 모든 날짜 검사 뒤에 수행한다.
            taxValidator.validateTransactions(requested);

            List<TaxTransactionDto> existing =
                    taxMapper.findTransactions(taxInputId, null);

            if (taxValidator.sameTransactions(existing, requested)) {
                savedRows = existing;

            } else {
                taxValidator.validateInitialSaveAllowed(existing);
                taxValidator.validateNewTransactionIds(rows);

                for (TaxTransactionDto row : requested) {
                    taxMapper.insertTransaction(row);
                }

                if (!requested.isEmpty()) {
                    invalidateAfterTransactionChange(taxInputId);
                }

                savedRows = taxMapper.findTransactions(
                        taxInputId,
                        null
                );
            }
        }

        return TaxServiceResponse.builder()
                .input(input)
                .transactions(savedRows)
                .build();
    }

    @Override
    @Transactional
    public TaxInputRequest update(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            Long taxInputId,
            TaxServiceRequest request
    ) {
        taxValidator.validateRequest(
                request,
                "수정 요청이 필요합니다."
        );

        boolean updateProfile = request.getInput() != null;
        boolean updateCategory =
                request.getTransactionId() != null
                        || request.getCategory() != null;

        taxValidator.validateUpdateRequest(taxInputId, request);

        Set<Long> ownedGuestInputIds =
                copyGuestInputIds(guestInputIds);

        TaxInputRequest existingInput = requireInput(
                principal,
                ownedGuestInputIds,
                taxInputId,
                true
        );

        TaxInputRequest updatedInput = existingInput;

        if (updateProfile) {
            updatedInput = buildInput(
                    request.getInput(),
                    taxInputId,
                    existingInput.getUserId()
            );

            taxValidator.validateInput(updatedInput);

            if (existingInput.getUserId() == null
                    && "SAVED".equals(updatedInput.getSaveStatus())) {
                Long userId =
                        taxValidator.requireLogin(principal);

                taxValidator.validateGuestOwnership(
                        ownedGuestInputIds,
                        taxInputId
                );

                updatedInput = buildInput(
                        updatedInput,
                        taxInputId,
                        userId
                );
            }
        }

        TaxTransactionDto existingTransaction = null;
        boolean categoryChanged = false;

        if (updateCategory) {
            existingTransaction = requireTransaction(
                    taxInputId,
                    request.getTransactionId()
            );

            categoryChanged = !Objects.equals(
                    existingTransaction.getCategory(),
                    request.getCategory()
            );
        }

        // 검증을 모두 마친 뒤 실제 변경을 시작한다.
        if (updateProfile) {
            taxMapper.updateInput(updatedInput);
        }

        if (categoryChanged) {
            Long transactionId = request.getTransactionId();
            String category = request.getCategory();

            log.debug(
                    "카테고리 수정 요청: 분석번호={}, 거래번호={}, 기존값={}, 요청값={}",
                    taxInputId,
                    transactionId,
                    existingTransaction.getCategory(),
                    category
            );

            int updated = taxMapper.updateCategory(
                    taxInputId,
                    transactionId,
                    category
            );

            log.debug(
                    "카테고리 UPDATE 결과: 분석번호={}, 거래번호={}, 반영행수={}",
                    taxInputId,
                    transactionId,
                    updated
            );

            taxValidator.validateCategoryUpdateResult(updated);

            TaxTransactionDto after = requireTransaction(
                    taxInputId,
                    transactionId
            );

            log.debug(
                    "카테고리 DB 재조회: 거래번호={}, 변경전={}, 요청값={}, DB조회값={}, 일치여부={}",
                    transactionId,
                    existingTransaction.getCategory(),
                    category,
                    after.getCategory(),
                    Objects.equals(category, after.getCategory())
            );
        }

        if (categoryChanged) {
            invalidateAfterTransactionChange(taxInputId);

        } else if (updateProfile) {
            taxMapper.deleteResult(taxInputId);
        }

        return updatedInput;
    }

    @Override
    @Transactional
    public void delete(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            Long taxInputId,
            TaxServiceRequest request,
            boolean deleteAll
    ) {
        taxValidator.validateRequest(
                request,
                "삭제 요청이 필요합니다."
        );

        Long transactionId = request.getTransactionId();

        taxValidator.validateDeleteRequest(
                taxInputId,
                request,
                deleteAll
        );

        Set<Long> ownedGuestInputIds =
                copyGuestInputIds(guestInputIds);

        requireInput(
                principal,
                ownedGuestInputIds,
                taxInputId,
                true
        );

        if (deleteAll) {
            int deleted = taxMapper.deleteInput(taxInputId);
            taxValidator.validateInputDeleteResult(deleted);
            return;
        }

        requireTransaction(taxInputId, transactionId);

        int deleted = taxMapper.deleteTransaction(
                taxInputId,
                transactionId
        );

        taxValidator.validateTransactionDeleteResult(deleted);
        invalidateAfterTransactionChange(taxInputId);
    }

    @Override
    public PdfParseResponseDto parse(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            Long taxInputId,
            MultipartFile file
    ) throws IOException {
        Set<Long> ownedGuestInputIds =
                copyGuestInputIds(guestInputIds);

        if (taxInputId != null) {
            requireInput(
                    principal,
                    ownedGuestInputIds,
                    taxInputId,
                    false
            );
        }

        try {
            byte[] bytes = taxValidator.validatePdf(file);

            List<PageOcrResultDto> pages =
                    taxOcrService.extractPages(bytes);

            PdfParseResponseDto response =
                    pythonPdfClient.analyze(
                            bytes,
                            file.getOriginalFilename(),
                            pages
                    );

            taxValidator.validatePdfResponse(response);

            List<TaxTransactionDto> transactions =
                    preparePdfTransactions(
                            response.getTransactions()
                    );

            long reviewCount = transactions.stream()
                    .filter(taxValidator::isReviewMarked)
                    .count();

            // 추출 결과는 반환만 하고 DB에 저장하지 않는다.
            PdfParseResponseDto result = PdfParseResponseDto.builder()
                    .fileName(response.getFileName())
                    .totalPages(response.getTotalPages())
                    .extractedCount(response.getExtractedCount())
                    .parsedCount(response.getParsedCount())
                    .failedCount(response.getFailedCount())
                    .pageResults(response.getPageResults())
                    .transactions(transactions)
                    .tables(response.getTables())
                    .persisted(false)
                    .taxInputIdsByYear(new LinkedHashMap<>())
                    .savedCount(0)
                    .build();

            log.info(
                    "PDF 파싱 완료. 거래={}건, 검토={}건, DB 저장 없음",
                    transactions.size(),
                    reviewCount
            );

            return result;

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

            TaxTransactionDto extracted = buildExtractedTransaction(
                    source,
                    source.getNeedsReview(),
                    source.getExcluded(),
                    source.getFailureReason()
            );

            if (taxValidator.requiresPdfReview(extracted)) {
                String reason = extracted.getFailureReason();

                result.add(buildReviewTransaction(
                        extracted,
                        reason == null || reason.isBlank()
                                ? "추출한 거래 내용을 확인해주세요."
                                : reason
                ));

                continue;
            }

            TaxTransactionDto stored;

            try {
                taxValidator.validatePdfTransactionBeforeConversion(
                        extracted
                );

                stored = buildStoredTransaction(extracted, null);

                taxValidator.validatePdfTransactionAfterConversion(
                        stored
                );

            } catch (ApiException exception) {
                String reason = exception.getMessage();

                // 검증 실패 거래를 버리지 않고 검토 대상으로 반환한다.
                result.add(buildReviewTransaction(
                        extracted,
                        reason == null || reason.isBlank()
                                ? "거래 내용을 확인해주세요."
                                : reason
                ));

                continue;
            }

            TaxTransactionDto row = TaxTransactionDto.builder()
                    .taxInputId(null)
                    .transactionId(null)
                    .transactionType(stored.getTransactionType())
                    .transactionDate(stored.getTransactionDate())
                    .description(stored.getDescription())
                    .category(stored.getCategory())
                    .preTaxIncome(stored.getPreTaxIncome())
                    .deductedTaxRate(stored.getDeductedTaxRate())
                    .amount(stored.getAmount())
                    .actualAmount(stored.getAmount())
                    .pageNumber(extracted.getPageNumber())
                    .rawText(extracted.getRawText())
                    .valueKind(extracted.getValueKind())
                    .amountBasis(extracted.getAmountBasis())
                    .taxDeducted(extracted.getTaxDeducted())
                    .documentYear(extracted.getDocumentYear())
                    .parseSuccess(true)
                    .needsReview(false)
                    .excluded(false)
                    .failureReason(null)
                    .build();

            result.add(row);
        }

        return result;
    }

    private TaxTransactionDto buildReviewTransaction(
            TaxTransactionDto source,
            String reason
    ) {
        String failureReason = mergeFailureReason(
                source.getFailureReason(),
                reason
        );

        return buildExtractedTransaction(
                source,
                true,
                true,
                failureReason
        );
    }

    private String mergeFailureReason(
            String current,
            String reason
    ) {
        if (current == null || current.isBlank()) {
            return reason;
        }

        if (current.equals(reason)) {
            return current;
        }

        for (String existing : current.split(" / ")) {
            if (existing.equals(reason)) {
                return current;
            }
        }

        return current + " / " + reason;
    }

    private TaxTransactionDto buildExtractedTransaction(
            TaxTransactionDto source,
            Boolean needsReview,
            Boolean excluded,
            String failureReason
    ) {
        // PDF 원본 내용은 보존하고 DB 식별자는 연결하지 않는다.
        return TaxTransactionDto.builder()
                .taxInputId(null)
                .transactionId(null)
                .transactionType(source.getTransactionType())
                .transactionDate(source.getTransactionDate())
                .description(source.getDescription())
                .category(source.getCategory())
                .preTaxIncome(source.getPreTaxIncome())
                .deductedTaxRate(source.getDeductedTaxRate())
                .amount(source.getAmount())
                .taxDeducted(source.getTaxDeducted())
                .actualAmount(source.getActualAmount())
                .pageNumber(source.getPageNumber())
                .rawText(source.getRawText())
                .parseSuccess(source.getParseSuccess())
                .excluded(excluded)
                .failureReason(failureReason)
                .transactionMonth(source.getTransactionMonth())
                .documentYear(source.getDocumentYear())
                .valueKind(source.getValueKind())
                .amountBasis(source.getAmountBasis())
                .needsReview(needsReview)
                .build();
    }

    private TaxTransactionDto buildStoredTransaction(
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

        // 실제 입출금액이 확인된 경우 우선 적용한다.
        BigDecimal amount = source.getActualAmount() != null
                ? source.getActualAmount()
                : source.getAmount();

        return TaxTransactionDto.builder()
                .taxInputId(taxInputId)
                .transactionType(type)
                .transactionDate(source.getTransactionDate())
                .description(source.getDescription())
                .category(category)
                .preTaxIncome(source.getPreTaxIncome())
                .deductedTaxRate(source.getDeductedTaxRate())
                .amount(amount)
                .build();
    }

    private TaxInputRequest buildInput(
            TaxInputRequest source,
            Long taxInputId,
            Long ownerId
    ) {
        taxValidator.validateInputSource(source);

        // 서버가 확인한 식별자와 소유자를 사용하고 누락값을 기본값으로 채운다.
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
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            Long taxInputId,
            boolean lock
    ) {
        taxValidator.validateId(taxInputId, "taxInputId");

        TaxInputRequest input = lock
                ? taxMapper.lockInput(taxInputId)
                : taxMapper.findInput(taxInputId);

        taxValidator.requireAccess(
                principal,
                guestInputIds,
                input
        );

        return input;
    }

    private TaxTransactionDto requireTransaction(
            Long taxInputId,
            Long transactionId
    ) {
        // 이 메서드에 들어오는 거래번호는 수정·삭제 요청 검사에서 검증했다.
        List<TaxTransactionDto> rows =
                taxMapper.findTransactions(
                        taxInputId,
                        transactionId
                );

        taxValidator.validateTransactionFound(rows);

        return rows.get(0);
    }

    private void invalidateAfterTransactionChange(Long taxInputId) {
        // 거래 변경 후 기존 분석 결과를 무효화한다.
        taxMapper.deleteResult(taxInputId);
        taxMapper.touchInput(taxInputId);
    }

    private Set<Long> copyGuestInputIds(Set<Long> guestInputIds) {
        return guestInputIds == null
                ? Set.of()
                : Set.copyOf(guestInputIds);
    }

    private String failureCode(Exception exception) {
        if (exception instanceof ApiException apiException) {
            return apiException.getCode();
        }

        return exception instanceof IOException
                ? "PDF_IO_ERROR"
                : "PDF_PROCESSING_FAILED";
    }
}