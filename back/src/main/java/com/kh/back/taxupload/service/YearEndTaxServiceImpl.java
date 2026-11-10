package com.kh.back.taxupload.service;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.exception.ApiException;
import com.kh.back.common.security.JwtTokenProvider;
import com.kh.back.taxupload.dto.PdfParseResponseDto;
import com.kh.back.taxupload.dto.TaxCategoryChange;
import com.kh.back.taxupload.dto.TaxServiceResponse;
import com.kh.back.taxupload.dto.TaxTransactionDto;
import com.kh.back.taxupload.dto.TaxTransactionDto.AccessContext;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxInputRequest;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxServiceRequest;
import com.kh.back.taxupload.mapper.TaxMapper;
import com.kh.back.taxupload.validator.TaxValidator;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class YearEndTaxServiceImpl implements YearEndTaxService {

    private static final Duration GUEST_TAX_TOKEN_LIFETIME = Duration.ofHours(24);

    private final TaxValidator taxValidator;
    private final TaxMapper taxMapper;
    private final PythonPdfClient pythonPdfClient;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 분석 기본정보와 최초 거래 목록을 같은 트랜잭션에서 저장한다.
     */
    @Override
    @Transactional
    public TaxServiceResponse save(
            CustomUserDetails principal,
            String guestAccessToken,
            TaxServiceRequest request
    ) {
        taxValidator.validateRequestPresent(request, "저장 요청이 필요합니다."); // request가 null 이면 저장요청이 필요합니다. 라는 메세지와 함께 400 bad request 오류 발생시킴

        AccessContext access = createAccess(principal, guestAccessToken); // 로그인 사용자 정보 principal와 비회원 토큰 guestAccessToken 으로 접근정보를 만듬 , access에는 회원번호 관리자 여부 비회원 토큰에서 검증된 분석번호가담김, 이후 기존 분석에 접근할수 있는지 판단할떄 사용
        Long taxInputId = request.getTaxInputId(); // 요청에 담긴 분석번호를 가져옴 taxinputid가 42라면 42번 분석을 대상으로 저장한다는뜻
        List<TaxTransactionDto> rows = request.getTransactions(); // 요청에 담긴 거래 목록을 가져옴 , 거래 목록을  보내지 않았다면 rows는 null 일수 있음.
        boolean createAnalysis = taxInputId == null; // 분석번호가 없으면 true가 되어 새 분석을 생성, 분석번호가 있으면 false가 되어 기존 분석의 접근 권한을 확인한뒤 그 분석에 거래를 저장

        if (!createAnalysis) {
            taxValidator.validateExistingAnalysisSave(request.getInput(), rows);
        }

        TaxInputRequest input;

        if (createAnalysis) {
            // 새 분석의 소유자는 서버의 인증 정보에서 결정한다.
            input = normalizeInput(request.getInput(), null, access.getUserId());
            taxValidator.validateInput(input);

            // 공개 저장에서는 새 분석을 임시 상태로 생성한다.
            input.setSaveStatus("TEMP");
            taxMapper.insertInput(input);
            taxInputId = input.getTaxInputId();
        } else {
            // 기존 분석에 거래를 저장하기 전에 접근 권한을 확인하고 분석을 잠근다.
            input = requireInput(access, taxInputId, true);
        }

        List<TaxTransactionDto> savedRows = List.of();

        if (rows != null) {
            List<TaxTransactionDto> requested = new ArrayList<>();

            for (TaxTransactionDto source : rows) {
                taxValidator.validateTransactionSource(source, taxInputId);
                taxValidator.validateCashTransaction(source);

                TaxTransactionDto row = copyStoredTransaction(source, taxInputId);

                taxValidator.validateStoredTransaction(row);
                taxValidator.validateStoredTransactionDate(row, input.getTaxYear());
                requested.add(row);
            }

            if (!requested.isEmpty()) {
                taxValidator.validateTransactions(requested);
            }

            List<TaxTransactionDto> existing = taxMapper.findTransactions(taxInputId, null);
            Integer expectedCount = request.getExpectedTransactionCount();

            if (expectedCount != null) {
                // 화면이 알고 있는 저장 건수와 비교해 추가 저장과 재시도를 구분한다.
                taxValidator.validateNewTransactionIds(rows);

                if (expectedCount < 0 || expectedCount > existing.size()) {
                    throw invalid("expectedTransactionCount", "저장된 거래 목록을 확인해주세요.");
                }

                if (existing.size() > expectedCount) {
                    savedRows = existing.subList(expectedCount, existing.size());

                    if (!sameTransactions(savedRows, requested)) {
                        throw new ApiException(
                                HttpStatus.CONFLICT,
                                "TRANSACTIONS_CHANGED",
                                "저장된 거래가 변경됐습니다. 목록을 확인해주세요."
                        );
                    }
                } else if (!requested.isEmpty()) {
                    insertTransactionsAndInvalidateResult(taxInputId, requested);

                    List<TaxTransactionDto> stored = taxMapper.findTransactions(taxInputId, null);
                    savedRows = stored.subList(expectedCount, stored.size());
                }
            } else if (sameTransactions(existing, requested)) {
                // 최초 저장 요청을 재시도하면 이미 저장된 거래를 돌려준다.
                savedRows = existing;
            } else {
                // 최초 저장 요청으로 기존 거래 전체를 교체하지 않는다.
                if (!existing.isEmpty()) {
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            "USE_TRANSACTION_CHANGE_API",
                            "기존 거래는 수정·삭제 API로 변경해주세요."
                    );
                }

                taxValidator.validateNewTransactionIds(rows);

                if (!requested.isEmpty()) {
                    insertTransactionsAndInvalidateResult(taxInputId, requested);
                }

                savedRows = taxMapper.findTransactions(taxInputId, null);
            }
        }

        String issuedGuestAccessToken = null;

        // 새 비회원 분석에만 해당 분석번호로 토큰을 발급한다.
        if (createAnalysis && access.getUserId() == null) {
            issuedGuestAccessToken = issueGuestAccessToken(taxInputId);
        }

        return TaxServiceResponse.builder()
                .input(input)
                .transactions(savedRows)
                .guestAccessToken(issuedGuestAccessToken)
                .build();
    }

    /**
     * 기본정보와 거래 카테고리를 한 요청에서 수정한다.
     */
    @Override
    @Transactional
    public TaxInputRequest update(
            CustomUserDetails principal,
            String guestAccessToken,
            Long taxInputId,
            TaxServiceRequest request
    ) {
        taxValidator.validateRequestPresent(request, "수정 요청이 필요합니다.");

        TaxInputRequest input = request.getInput();
        List<TaxCategoryChange> changes = request.getChanges();
        boolean updateProfile = input != null;
        boolean updateCategory = changes != null && !changes.isEmpty();

        taxValidator.validateUpdateTargets(updateProfile, updateCategory);
        taxValidator.validateMatchingTaxInputId(taxInputId, request.getTaxInputId());

        AccessContext access = createAccess(principal, guestAccessToken);

        // 기본정보와 카테고리가 같은 분석을 기준으로 수정되도록 먼저 잠근다.
        TaxInputRequest existingInput = requireInput(access, taxInputId, true);
        TaxInputRequest updatedInput = existingInput;

        if (updateProfile) {
            updatedInput = normalizeInput(input, taxInputId, existingInput.getUserId());
            taxValidator.validateInput(updatedInput);

            // 기본정보를 수정해도 기존 소유자와 저장 상태를 유지한다.
            updatedInput.setSaveStatus(existingInput.getSaveStatus());
            taxMapper.updateInput(updatedInput);
        }

        // 카테고리 변경과 결과 무효화를 같은 트랜잭션에서 처리한다.
        if (updateCategory) {
            applyCategoryChanges(taxInputId, changes);
        } else if (updateProfile) {
            taxMapper.deleteResult(taxInputId);
        }

        return updatedInput;
    }

    /**
     * 분석 전체 삭제와 거래 삭제를 처리한다.
     */
    @Override
    @Transactional
    public void delete(
            CustomUserDetails principal,
            String guestAccessToken,
            Long taxInputId,
            TaxServiceRequest request,
            boolean deleteAll
    ) {
        taxValidator.validateRequestPresent(request, "삭제 요청이 필요합니다.");

        Long transactionId = request.getTransactionId();
        taxValidator.validateMatchingTaxInputId(taxInputId, request.getTaxInputId());

        List<Long> requestedIds = request.getTransactionIds();
        taxValidator.validateDeleteTargets(deleteAll, transactionId, requestedIds);

        AccessContext access = createAccess(principal, guestAccessToken);

        // 삭제 전에 분석 접근 권한을 확인하고 분석을 잠근다.
        requireInput(access, taxInputId, true);

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

        // 목록 삭제는 이미 삭제된 번호가 있어도 재시도할 수 있다.
        if (requestedIds != null) {
            int deleted = taxMapper.deleteTransactionBatch(taxInputId, Set.copyOf(requestedIds));

            if (deleted > 0) {
                invalidateAfterTransactionChange(taxInputId);
            }

            return;
        }

        // 번호 형식은 검증을 마쳤으므로 거래가 해당 분석에 속하는지만 확인한다.
        requireTransaction(taxInputId, transactionId);

        int deleted = taxMapper.deleteTransaction(taxInputId, transactionId);

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
     * 추출한 거래의 DB 저장은 save에서 처리한다.
     */
    @Override
    public PdfParseResponseDto parse(
            CustomUserDetails principal,
            String guestAccessToken,
            Long taxInputId,
            MultipartFile file
    ) throws IOException {
        AccessContext access = createAccess(principal, guestAccessToken);

        if (taxInputId != null) {
            requireInput(access, taxInputId, false);
        }

        try {
            byte[] bytes = taxValidator.validatePdf(file);

            // Python에서 PDF 분석과 OCR을 함께 수행하도록 원본을 전달한다.
            PdfParseResponseDto response = pythonPdfClient.analyze(
                    bytes,
                    file.getOriginalFilename()
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

            List<TaxTransactionDto> transactions = preparePdfTransactions(response.getTransactions());

            long reviewCount = transactions.stream()
                    .filter(row -> Boolean.TRUE.equals(row.getNeedsReview())
                            || Boolean.TRUE.equals(row.getExcluded()))
                    .count();

            // 추출 결과와 저장 상태를 구분해 반환한다.
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
            log.warn("PDF 파싱 실패. 코드={}", failureCode(exception));
            throw exception;
        }
    }

    private List<TaxTransactionDto> preparePdfTransactions(List<TaxTransactionDto> sources) {
        List<TaxTransactionDto> result = new ArrayList<>();

        for (TaxTransactionDto source : sources) {
            if (source == null) {
                throw invalid("transactions", "PDF 분석 결과에 빈 거래가 있습니다.");
            }

            // PDF 추출 데이터의 식별자를 비워 DB에 저장된 거래와 구분한다.
            source.setTaxInputId(null);
            source.setTransactionId(null);

            TaxTransactionDto row;

            try {
                row = copyStoredTransaction(source, null);
                taxValidator.validateExtractedPdfTransaction(source, row);
            } catch (ApiException exception) {
                String reason = exception.getMessage();

                // 검증에 실패한 거래는 화면에서 확인할 수 있도록 검토 대상으로 반환한다.
                markPdfReview(
                        source,
                        reason == null || reason.isBlank()
                                ? "거래 내용을 확인해주세요."
                                : reason
                );

                result.add(source);
                continue;
            }

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

    private void markPdfReview(TaxTransactionDto row, String reason) {
        // 검토 대상 거래는 응답에 포함하고 저장 대상에서 제외한다.
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

    private TaxTransactionDto copyStoredTransaction(TaxTransactionDto source, Long taxInputId) {
        String type = source.getTransactionType();

        if (type == null || type.isBlank()) {
            type = "UNKNOWN";
        }

        String category = source.getCategory();

        if (category == null || category.isBlank()) {
            category = "UNCLASSIFIED";
        }

        // 실제 입출금액이 있으면 PDF 추출과 거래 저장에서 모두 우선 사용한다.
        BigDecimal actualAmount = source.getActualAmount();
        BigDecimal amount = actualAmount == null ? source.getAmount() : actualAmount;

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

    private TaxInputRequest normalizeInput(
            TaxInputRequest source,
            Long taxInputId,
            Long ownerId
    ) {
        taxValidator.validateInputPresent(source);

        // 분석번호와 소유자는 서버에서 확인한 값을 사용한다.
        return TaxInputRequest.builder()
                .taxInputId(taxInputId)
                .userId(ownerId)
                .taxYear(source.getTaxYear())
                .businessRegistered(source.getBusinessRegistered())
                .dependentCount(
                        source.getDependentCount() == null ? 0 : source.getDependentCount()
                )
                .generalDonationAmount(
                        source.getGeneralDonationAmount() == null
                                ? BigDecimal.ZERO
                                : source.getGeneralDonationAmount()
                )
                .specialDonationAmount(
                        source.getSpecialDonationAmount() == null
                                ? BigDecimal.ZERO
                                : source.getSpecialDonationAmount()
                )
                .saveStatus(source.getSaveStatus() == null ? "TEMP" : source.getSaveStatus())
                .build();
    }

    private TaxInputRequest requireInput(AccessContext access, Long taxInputId, boolean lock) {
        taxValidator.validateId(taxInputId, "taxInputId");

        TaxInputRequest input = lock
                ? taxMapper.lockInput(taxInputId)
                : taxMapper.findInput(taxInputId);

        taxValidator.requireAccess(access, input);

        return input;
    }

    private void applyCategoryChanges(Long taxInputId, List<TaxCategoryChange> changes) {
        Set<Long> seenTransactionIds = new HashSet<>();
        List<TaxCategoryChange.Feedback> feedback = new ArrayList<>();

        for (TaxCategoryChange change : changes) {
            taxValidator.validateCategoryChangeTarget(change, seenTransactionIds);

            TaxTransactionDto row = requireTransaction(taxInputId, change.transactionId());
            taxValidator.validateCategory(change.category(), row);

            feedback.add(new TaxCategoryChange.Feedback(
                    row.getTransactionId(),
                    row.getTransactionType(),
                    row.getDescription(),
                    change.category()
            ));
        }

        // 검증을 마친 카테고리 목록을 한 번에 반영한다.
        if (taxMapper.updateCategories(taxInputId, changes) != changes.size()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "TRANSACTION_UPDATE_FAILED",
                    "거래 수정 결과를 확인하지 못했습니다."
            );
        }

        invalidateAfterTransactionChange(taxInputId);

        try {
            // 학습 후보 전달까지 성공해야 카테고리 변경을 커밋한다.
            pythonPdfClient.sendTrainingFeedback(feedback);
        } catch (RestClientException exception) {
            log.warn("재학습 후보 전달 실패: 분석번호={}", taxInputId, exception);

            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "TRAINING_FEEDBACK_FAILED",
                    "재학습 데이터 전달에 실패해 카테고리 수정을 취소했습니다."
            );
        }
    }

    private TaxTransactionDto requireTransaction(Long taxInputId, Long transactionId) {
        List<TaxTransactionDto> rows = taxMapper.findTransactions(taxInputId, transactionId);

        if (rows.isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "TRANSACTION_NOT_FOUND",
                    "해당 분석의 거래를 찾을 수 없습니다."
            );
        }

        return rows.get(0);
    }

    private void insertTransactionsAndInvalidateResult(
            Long taxInputId,
            List<TaxTransactionDto> transactions
    ) {
        taxMapper.insertTransactions(transactions);
        invalidateAfterTransactionChange(taxInputId);
    }

    private void invalidateAfterTransactionChange(Long taxInputId) {
        // 거래 변경 후 이전 계산 결과를 무효화하고 분석 수정 시각을 갱신한다.
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

            if (!Objects.equals(left.getTransactionType(), right.getTransactionType())
                    || !Objects.equals(left.getTransactionDate(), right.getTransactionDate())
                    || !Objects.equals(left.getDescription(), right.getDescription())
                    || !Objects.equals(left.getCategory(), right.getCategory())
                    || !sameDecimal(left.getAmount(), right.getAmount())
                    || !sameDecimal(left.getPreTaxIncome(), right.getPreTaxIncome())
                    || !sameDecimal(left.getDeductedTaxRate(), right.getDeductedTaxRate())) {
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

    private AccessContext createAccess(
            CustomUserDetails principal,
            String guestAccessToken
    ) {
        Long userId = principal == null ? null : principal.getUserId();

        boolean admin = principal != null
                && principal.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));

        // Validator에서 검증한 분석번호만 접근 정보에 담는다.
        Long guestTaxInputId = taxValidator.readGuestTaxInputId(guestAccessToken);

        return AccessContext.builder()
                .userId(userId)
                .admin(admin)
                .guestTaxInputId(guestTaxInputId)
                .build();
    }

    private String issueGuestAccessToken(Long taxInputId) {
        Instant now = Instant.now();

        // 비회원 토큰의 권한을 생성한 분석 한 건으로 제한한다.
        return Jwts.builder()
                .claim("type", TaxValidator.GUEST_TAX_TOKEN_TYPE)
                .claim("taxInputId", taxInputId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(GUEST_TAX_TOKEN_LIFETIME)))
                .signWith(jwtTokenProvider.getSecretKey())
                .compact();
    }

    private ApiException invalid(String field, String message) {
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

        return exception instanceof IOException ? "PDF_IO_ERROR" : "PDF_PROCESSING_FAILED";
    }
}