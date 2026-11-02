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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    /**
     * 분석 기본정보와 전체 거래를 함께 조회한다.
     */
    @Override
    @Transactional(readOnly = true)
    public TaxServiceResponse getTaxData(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            Long taxInputId
    ) {
        Set<Long> ownedGuestInputIds =
                copyGuestInputIds(guestInputIds);

        // 인증 정보와 비회원 세션으로 분석 접근 권한을 확인한다.
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

    /**
     * 분석 기본정보 생성과 거래 최초 저장을 처리한다.
     */
    @Override
    @Transactional
    public TaxServiceResponse save(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            TaxServiceRequest request
    ) {
        if (request == null) {
            throw invalid(
                    "request",
                    "저장 요청이 필요합니다."
            );
        }

        Set<Long> ownedGuestInputIds =
                copyGuestInputIds(guestInputIds);

        Long taxInputId = request.getTaxInputId();
        List<TaxTransactionDto> rows = request.getTransactions();

        boolean createAnalysis = taxInputId == null;

        // 기존 분석의 기본정보 변경은 수정 API에서 처리한다.
        if (!createAnalysis && request.getInput() != null) {
            throw invalid(
                    "input",
                    "기존 분석의 기본정보는 수정 API를 이용해주세요."
            );
        }

        if (!createAnalysis && rows == null) {
            throw invalid(
                    "transactions",
                    "저장할 거래 목록이 필요합니다."
            );
        }

        if (rows != null) {
            taxValidator.validateTransactionRows(rows);
        }

        TaxInputRequest input;

        if (createAnalysis) {
            // 새 분석의 소유자는 요청 본문이 아닌 인증 정보에서 결정한다.
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

                // 정식 저장의 소유자를 인증 정보로 확정한 새 DTO를 만든다.
                input = buildInput(
                        input,
                        null,
                        loginUserId
                );
            }

            // 자동 생성된 분석번호는 MyBatis가 input에 채운다.
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
            List<TaxTransactionDto> requested =
                    new ArrayList<>();

            for (TaxTransactionDto source : rows) {
                taxValidator.validateTransactionSource(
                        source,
                        taxInputId
                );

                taxValidator.validateCashTransaction(source);

                // 실제 입출금액 선택과 저장 필드 복사를 Builder에서 처리한다.
                TaxTransactionDto row = buildStoredTransaction(
                        source,
                        taxInputId
                );

                taxValidator.validateStoredTransaction(row);

                taxValidator.validateStoredTransactionDate(
                        row,
                        input.getTaxYear()
                );

                requested.add(row);
            }

            if (!requested.isEmpty()) {
                taxValidator.validateTransactions(requested);
            }

            List<TaxTransactionDto> existing =
                    taxMapper.findTransactions(taxInputId, null);

            // 같은 저장 요청을 재전송하면 기존 거래를 반환한다.
            if (sameTransactions(existing, requested)) {
                savedRows = existing;

            } else {
                // 최초 저장 API로 기존 거래 전체를 교체하지 않는다.
                if (!existing.isEmpty()) {
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            "USE_TRANSACTION_CHANGE_API",
                            "기존 거래는 수정·삭제 API로 변경해주세요."
                    );
                }

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

    /**
     * 기본정보와 거래 카테고리를 한 요청에서 수정한다.
     */
    @Override
    @Transactional
    public TaxInputRequest update(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            Long taxInputId,
            TaxServiceRequest request
    ) {
        if (request == null) {
            throw invalid(
                    "request",
                    "수정 요청이 필요합니다."
            );
        }

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

        if (request.getTaxInputId() != null
                && !Objects.equals(
                request.getTaxInputId(),
                taxInputId
        )) {
            throw invalid(
                    "taxInputId",
                    "경로와 요청 본문의 분석번호가 다릅니다."
            );
        }

        if (updateCategory) {
            taxValidator.validateId(
                    request.getTransactionId(),
                    "transactionId"
            );

            taxValidator.validateCategory(
                    request.getCategory()
            );
        }

        Set<Long> ownedGuestInputIds =
                copyGuestInputIds(guestInputIds);

        // 같은 분석에 대한 변경이 충돌하지 않도록 부모 분석을 잠근다.
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

            // 직접 만든 비회원 분석만 로그인한 회원 소유로 전환한다.
            if (existingInput.getUserId() == null
                    && "SAVED".equals(updatedInput.getSaveStatus())) {
                Long userId =
                        taxValidator.requireLogin(principal);

                if (!ownedGuestInputIds.contains(taxInputId)) {
                    throw new ApiException(
                            HttpStatus.FORBIDDEN,
                            "ACCESS_DENIED",
                            "직접 생성한 비회원 분석만 저장할 수 있습니다."
                    );
                }

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

        // 필요한 검증이 모두 끝난 뒤 DB 변경을 시작한다.
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

            if (updated != 1) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "TRANSACTION_UPDATE_FAILED",
                        "거래 수정 결과를 확인하지 못했습니다."
                );
            }

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

        // 기본정보와 카테고리를 함께 바꿔도 무효화는 한 번만 수행한다.
        if (categoryChanged) {
            invalidateAfterTransactionChange(taxInputId);

        } else if (updateProfile) {
            taxMapper.deleteResult(taxInputId);
        }

        return updatedInput;
    }

    /**
     * 분석 전체 삭제와 거래 한 건 삭제를 처리한다.
     */
    @Override
    @Transactional
    public void delete(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            Long taxInputId,
            TaxServiceRequest request,
            boolean deleteAll
    ) {
        if (request == null) {
            throw invalid(
                    "request",
                    "삭제 요청이 필요합니다."
            );
        }

        Long transactionId = request.getTransactionId();

        if (request.getTaxInputId() != null
                && !Objects.equals(
                request.getTaxInputId(),
                taxInputId
        )) {
            throw invalid(
                    "taxInputId",
                    "경로와 요청 본문의 분석번호가 다릅니다."
            );
        }

        if (deleteAll && transactionId != null) {
            throw invalid(
                    "transactionId",
                    "분석 전체 삭제에는 거래번호를 전달할 수 없습니다."
            );
        }

        if (!deleteAll) {
            taxValidator.validateId(
                    transactionId,
                    "transactionId"
            );
        }

        Set<Long> ownedGuestInputIds =
                copyGuestInputIds(guestInputIds);

        // 삭제 전에 접근 권한을 확인하고 부모 분석을 잠근다.
        requireInput(
                principal,
                ownedGuestInputIds,
                taxInputId,
                true
        );

        if (deleteAll) {
            int deleted = taxMapper.deleteInput(taxInputId);

            if (deleted != 1) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "TAX_INPUT_DELETE_FAILED",
                        "분석 삭제 결과를 확인하지 못했습니다."
                );
            }

            return;
        }

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

    /**
     * PDF에서 거래 내역을 추출한다.
     * 추출 결과는 DB에 저장하지 않는다.
     */
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

            // 기존 추출 정보는 보존하고 저장 상태만 미저장으로 확정한다.
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
        List<TaxTransactionDto> result =
                new ArrayList<>();

        for (TaxTransactionDto source : sources) {
            if (source == null) {
                throw invalid(
                        "transactions",
                        "PDF 분석 결과에 빈 거래가 있습니다."
                );
            }

            // 추출 데이터의 식별자를 사용하지 않는 새 응답 DTO를 만든다.
            TaxTransactionDto extracted = buildExtractedTransaction(
                    source,
                    source.getNeedsReview(),
                    source.getExcluded(),
                    source.getFailureReason()
            );

            if (!Boolean.TRUE.equals(extracted.getParseSuccess())
                    || Boolean.TRUE.equals(extracted.getNeedsReview())
                    || Boolean.TRUE.equals(extracted.getExcluded())) {
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
                taxValidator.validateCashTransaction(extracted);
                taxValidator.validatePdfTransactionDate(extracted);

                stored = buildStoredTransaction(
                        extracted,
                        null
                );

                taxValidator.validateStoredTransaction(stored);
                taxValidator.validateTransactions(List.of(stored));

            } catch (ApiException exception) {
                String reason = exception.getMessage();

                // 검증 실패 거래도 버리지 않고 검토 대상으로 반환한다.
                result.add(buildReviewTransaction(
                        extracted,
                        reason == null || reason.isBlank()
                                ? "거래 내용을 확인해주세요."
                                : reason
                ));

                continue;
            }

            // 검증된 저장 필드와 PDF 부가 정보를 조합해 정상 거래를 만든다.
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

        // 검토 대상은 내용을 보존하되 저장 대상에서 제외한다.
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
        // PDF 원본 정보는 보존하되 DB 식별자는 응답에 연결하지 않는다.
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

        // 실제 입출금액이 확인됐으면 해당 금액을 저장에 우선 사용한다.
        BigDecimal amount = source.getActualAmount() != null
                ? source.getActualAmount()
                : source.getAmount();

        // DB 저장에 필요한 필드만 새 객체에 복사한다.
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
        if (source == null) {
            throw invalid(
                    "input",
                    "분석 기본정보가 필요합니다."
            );
        }

        // 분석번호와 소유자는 서버에서 확인한 값으로 고정한다.
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
        taxValidator.validateId(
                taxInputId,
                "taxInputId"
        );

        TaxInputRequest input = lock
                ? taxMapper.lockInput(taxInputId)
                : taxMapper.findInput(taxInputId);

        // 인증 객체와 비회원 세션 권한을 직접 전달한다.
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
        taxValidator.validateId(
                transactionId,
                "transactionId"
        );

        List<TaxTransactionDto> rows =
                taxMapper.findTransactions(
                        taxInputId,
                        transactionId
                );

        if (rows.isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "TRANSACTION_NOT_FOUND",
                    "해당 분석의 거래를 찾을 수 없습니다."
            );
        }

        return rows.get(0);
    }

    private void invalidateAfterTransactionChange(
            Long taxInputId
    ) {
        // 거래 변경 후 기존 결과를 무효화하고 분석 수정 시각을 갱신한다.
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

    private Set<Long> copyGuestInputIds(
            Set<Long> guestInputIds
    ) {
        // 요청 처리 중에는 비회원 권한 목록의 복사본을 사용한다.
        return guestInputIds == null
                ? Set.of()
                : Set.copyOf(guestInputIds);
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
}