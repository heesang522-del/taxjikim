package com.kh.back.taxupload.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.exception.ApiException;
import com.kh.back.taxupload.dto.PageOcrResultDto;
import com.kh.back.taxupload.dto.PdfParseResponseDto;
import com.kh.back.taxupload.dto.TaxTransactionDto;
import com.kh.back.taxupload.dto.TaxTransactionDto.AccessContext;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxInputRequest;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxResultResponse;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxResultRow;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxServiceRequest;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxServiceResponse;
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

    /**
     * 새 분석 기본정보를 생성한다.
     */
    @Override
    @Transactional
    public TaxInputRequest save(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            TaxInputRequest request
    ) {
        AccessContext access = createAccess(principal, guestInputIds);

        // 소유자는 요청 본문 대신 인증 정보에서 결정한다.
        TaxInputRequest input = normalizeInput(
                request,
                null,
                access.getUserId()
        );

        taxValidator.validateInput(input);

        // 비회원은 TEMP로 생성할 수 있고 SAVED에는 로그인이 필요하다.
        if ("SAVED".equals(input.getSaveStatus())) {
            input.setUserId(taxValidator.requireLogin(access));
        }

        taxMapper.insertInput(input);

        return input;
    }

    /**
     * 분석·저장 목록·거래 목록·계산 결과 조회를 통합한다.
     */
    @Override
    @Transactional(readOnly = true)
    public TaxServiceResponse get(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            TaxServiceRequest request
    ) {
        if (request == null) {
            throw invalid("request", "조회 요청이 필요합니다.");
        }

        String target = request.getTarget();

        // 잘못된 구분값은 다른 조회로 해석하지 않고 거절한다.
        if (!"INPUT".equals(target)
                && !"SAVED_INPUTS".equals(target)
                && !"TRANSACTIONS".equals(target)
                && !"RESULT".equals(target)) {
            throw invalid("target", "지원하지 않는 조회 대상입니다.");
        }

        AccessContext access = createAccess(principal, guestInputIds);

        // 저장 목록 조회는 회원 전용이며 특정 분석번호를 요구하지 않는다.
        if ("SAVED_INPUTS".equals(target)) {
            Long loginUserId = taxValidator.requireLogin(access);

            if (request.getTaxYear() != null) {
                taxValidator.validateTaxYear(request.getTaxYear());
            }

            return TaxServiceResponse.builder()
                    .inputs(
                            taxMapper.findSavedInputs(
                                    loginUserId,
                                    request.getTaxYear()
                            )
                    )
                    .build();
        }

        Long taxInputId = request.getTaxInputId();

        // 나머지 조회는 해당 분석에 대한 권한을 먼저 확인한다.
        TaxInputRequest input =
                requireInput(access, taxInputId, false);

        if ("INPUT".equals(target)) {
            return TaxServiceResponse.builder()
                    .input(input)
                    .build();
        }

        if ("TRANSACTIONS".equals(target)) {
            return TaxServiceResponse.builder()
                    .transactions(
                            taxMapper.findTransactions(taxInputId, null)
                    )
                    .build();
        }

        return TaxServiceResponse.builder()
                .result(readStoredResult(taxInputId))
                .build();
    }

    /**
     * 거래 목록을 최초 저장한다.
     * 기존 거래 전체를 새 목록으로 교체하지 않는다.
     */
    @Override
    @Transactional
    public List<TaxTransactionDto> transaction(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            Long taxInputId,
            List<TaxTransactionDto> rows
    ) {
        AccessContext access = createAccess(principal, guestInputIds);

        TaxInputRequest input =
                requireInput(access, taxInputId, true);

        taxValidator.validateTransactionRows(rows);

        List<TaxTransactionDto> requested = new ArrayList<>();

        for (TaxTransactionDto source : rows) {
            taxValidator.validateTransactionSource(source, taxInputId);
            taxValidator.validateCashTransaction(source);

            TaxTransactionDto row =
                    copyStoredTransaction(source, taxInputId);

            // 실제 입출금액이 있으면 저장 금액에 우선 적용한다.
            if (source.getActualAmount() != null) {
                row.setAmount(source.getActualAmount());
            }

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

        // 동일한 저장 재요청은 INSERT하지 않고 기존 목록을 반환한다.
        if (sameTransactions(existing, requested)) {
            return existing;
        }

        if (!existing.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "USE_TRANSACTION_CHANGE_API",
                    "기존 거래는 개별 수정·삭제 API로 변경해주세요."
            );
        }

        taxValidator.validateNewTransactionIds(rows);

        for (TaxTransactionDto row : requested) {
            taxMapper.insertTransaction(row);
        }

        if (!requested.isEmpty()) {
            invalidateAfterTransactionChange(taxInputId);
        }

        return taxMapper.findTransactions(taxInputId, null);
    }

    /**
     * INPUT은 분석 기본정보, CATEGORY는 거래 카테고리를 수정한다.
     * 반환값은 수정 대상이 속한 분석의 기본정보다.
     */
    @Override
    @Transactional
    public TaxInputRequest update(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            TaxServiceRequest request
    ) {
        if (request == null) {
            throw invalid("request", "수정 요청이 필요합니다.");
        }

        String target = request.getTarget();

        if (!"INPUT".equals(target)
                && !"CATEGORY".equals(target)) {
            throw invalid("target", "지원하지 않는 수정 대상입니다.");
        }

        // 수정 대상과 전달값이 서로 섞이지 않도록 검사한다.
        if ("INPUT".equals(target)
                && (request.getTransactionId() != null
                || request.getCategory() != null)) {
            throw invalid(
                    "request",
                    "분석 기본정보 수정에는 거래번호와 카테고리를 전달할 수 없습니다."
            );
        }

        if ("CATEGORY".equals(target)) {
            taxValidator.validateId(
                    request.getTransactionId(),
                    "transactionId"
            );
            taxValidator.validateCategory(request.getCategory());

            if (request.getInput() != null) {
                throw invalid(
                        "input",
                        "카테고리 수정에는 분석 기본정보를 전달할 수 없습니다."
                );
            }
        }

        AccessContext access = createAccess(principal, guestInputIds);
        Long taxInputId = request.getTaxInputId();

        // 두 수정 작업 모두 부모 분석의 권한 확인과 잠금을 먼저 수행한다.
        TaxInputRequest existingInput =
                requireInput(access, taxInputId, true);

        if ("INPUT".equals(target)) {
            TaxInputRequest updated = normalizeInput(
                    request.getInput(),
                    taxInputId,
                    existingInput.getUserId()
            );

            taxValidator.validateInput(updated);

            // 직접 만든 비회원 분석만 현재 회원 소유로 전환할 수 있다.
            if (existingInput.getUserId() == null
                    && "SAVED".equals(updated.getSaveStatus())) {
                Long loginUserId = taxValidator.requireLogin(access);

                if (!access.getGuestInputIds().contains(taxInputId)) {
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

        Long transactionId = request.getTransactionId();
        String category = request.getCategory();

        TaxTransactionDto existing =
                requireTransaction(taxInputId, transactionId);

        log.debug(
                "카테고리 수정 요청: 분석번호={}, 거래번호={}, 기존값={}, 요청값={}",
                taxInputId,
                transactionId,
                existing.getCategory(),
                category
        );

        // 기존값과 같으면 SQL 수정과 후처리를 생략한다.
        if (Objects.equals(existing.getCategory(), category)) {
            log.debug(
                    "카테고리 수정 생략: 거래번호={}, 기존값과 요청값이 같음",
                    transactionId
            );

            return existingInput;
        }

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

        TaxTransactionDto after =
                requireTransaction(taxInputId, transactionId);

        log.debug(
                "카테고리 DB 재조회: 거래번호={}, 변경전={}, 요청값={}, DB조회값={}, 일치여부={}",
                transactionId,
                existing.getCategory(),
                category,
                after.getCategory(),
                Objects.equals(category, after.getCategory())
        );

        invalidateAfterTransactionChange(taxInputId);

        return existingInput;
    }

    /**
     * INPUT은 분석 전체, TRANSACTION은 거래 한 건을 삭제한다.
     */
    @Override
    @Transactional
    public void delete(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            TaxServiceRequest request
    ) {
        if (request == null) {
            throw invalid("request", "삭제 요청이 필요합니다.");
        }

        String target = request.getTarget();

        if (!"INPUT".equals(target)
                && !"TRANSACTION".equals(target)) {
            throw invalid("target", "지원하지 않는 삭제 대상입니다.");
        }

        if ("INPUT".equals(target)
                && request.getTransactionId() != null) {
            throw invalid(
                    "transactionId",
                    "분석 전체 삭제에는 거래번호를 전달할 수 없습니다."
            );
        }

        // 거래번호 누락을 분석 전체 삭제로 처리하지 않는다.
        if ("TRANSACTION".equals(target)) {
            taxValidator.validateId(
                    request.getTransactionId(),
                    "transactionId"
            );
        }

        AccessContext access = createAccess(principal, guestInputIds);
        Long taxInputId = request.getTaxInputId();

        requireInput(access, taxInputId, true);

        if ("INPUT".equals(target)) {
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

        Long transactionId = request.getTransactionId();

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
    public TaxResultResponse calculateInput(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            Long taxInputId
    ) {
        AccessContext access = createAccess(principal, guestInputIds);

        requireInput(access, taxInputId, false);

        // 현재 구현은 계산 엔진 미연결 상태를 반환한다.
        ApiException exception = new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "CALCULATOR_NOT_CONFIGURED",
                "세금 계산 엔진이 아직 연결되지 않았습니다."
        );

        writeProcessLog(
                taxInputId,
                access.getUserId(),
                "CALC",
                "FAIL",
                exception.getCode()
        );

        throw exception;
    }

    @Override
    public PdfParseResponseDto parsePdf(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            Long taxInputId,
            MultipartFile file
    ) throws IOException {
        AccessContext access = createAccess(principal, guestInputIds);

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
                    preparePdfTransactions(response.getTransactions());

            long reviewCount = transactions.stream()
                    .filter(row ->
                            Boolean.TRUE.equals(row.getNeedsReview())
                                    || Boolean.TRUE.equals(row.getExcluded())
                    )
                    .count();

            // 추출만 수행했으며 거래 저장은 별도 요청에서 처리한다.
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

    private TaxResultResponse readStoredResult(Long taxInputId) {
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

            // 기존 날짜·시간에 한국 시간 오프셋을 붙인다.
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

            // 추출 데이터에는 DB 식별자를 연결하지 않는다.
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
                taxValidator.validateCashTransaction(source);
                taxValidator.validatePdfTransactionDate(source);

                row = copyStoredTransaction(source, null);

                if (source.getActualAmount() != null) {
                    row.setAmount(source.getActualAmount());
                }

                taxValidator.validateStoredTransaction(row);
                taxValidator.validateTransactions(List.of(row));

            } catch (ApiException exception) {
                String reason = exception.getMessage();

                // 검증 실패 거래는 버리지 않고 검토 대상으로 반환한다.
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
        // 검토 대상은 응답에 포함하되 저장 대상에서는 제외한다.
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

        // 저장할 필드만 복사해 새 객체를 생성한다.
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

        // 식별자와 소유자는 서버에서 확인한 값을 사용한다.
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
        // null 거래번호가 전체 조회로 해석되지 않도록 먼저 검사한다.
        taxValidator.validateId(transactionId, "transactionId");

        List<TaxTransactionDto> rows = taxMapper.findTransactions(
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

    private void invalidateAfterTransactionChange(Long taxInputId) {
        // 거래 변경 후 기존 계산 결과와 부모 분석의 수정 시각을 정리한다.
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

    private AccessContext createAccess(
            CustomUserDetails principal,
            Set<Long> guestInputIds
    ) {
        Long userId = principal == null
                ? null
                : principal.getUserId();

        boolean admin = principal != null
                && principal.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(
                                authority.getAuthority()
                        )
                );

        // 로그인한 뒤에도 직접 만든 비회원 분석의 저장 전환에 사용한다.
        Set<Long> ownedGuestInputIds = guestInputIds == null
                ? Set.of()
                : Set.copyOf(guestInputIds);

        return AccessContext.builder()
                .userId(userId)
                .admin(admin)
                .guestInputIds(ownedGuestInputIds)
                .build();
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

    private void writeProcessLog(
            Long taxInputId,
            Long userId,
            String processType,
            String status,
            String failReason
    ) {
        TransactionTemplate template =
                new TransactionTemplate(transactionManager);

        // 본 작업의 롤백과 실패 이력 저장을 분리한다.
        template.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );

        try {
            template.executeWithoutResult(
                    transactionStatus ->
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