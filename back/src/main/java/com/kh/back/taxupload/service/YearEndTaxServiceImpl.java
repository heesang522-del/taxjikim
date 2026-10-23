package com.kh.back.taxupload.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class YearEndTaxServiceImpl implements YearEndTaxService {

    private static final ZoneId SEOUL =
            ZoneId.of("Asia/Seoul");

    private final TaxValidator taxValidator;
    private final TaxMapper taxMapper;
    private final TaxOcrService taxOcrService;
    private final PythonPdfClient pythonPdfClient;
    private final PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

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
            Long loginUserId = taxValidator.requireLogin(access);

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

        /*
         * updateInput SQL에서 수정 시각을 이미 갱신한다.
         * 여기에서는 결과만 삭제해 중복 UPDATE를 피한다.
         */
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
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "저장할 거래 목록이 필요합니다.",
                    Map.of("rows", "거래 목록을 전달해주세요.")
            );
        }

        /*
         * 빈 배열은 전체 거래 삭제를 의미한다.
         * 비어 있지 않은 경우 기존 Validator로 모두 검증한다.
         */
        if (!rows.isEmpty()) {
            taxValidator.validateTransactions(rows);
        }

        List<TaxTransactionDto> existing =
                taxMapper.findTransactions(taxInputId);

        /*
         * 동일한 순서와 동일한 값의 목록을 재전송하면
         * 거래번호와 기존 계산 결과를 유지한다.
         */
        if (sameTransactions(existing, rows)) {
            return existing;
        }

        /*
         * 이 API는 거래 추가가 아니라 전체 목록 교체다.
         * 삭제와 삽입 중 오류가 나면 동일 트랜잭션으로 롤백된다.
         */
        taxMapper.deleteTransactions(taxInputId);

        List<TaxTransactionDto> saved = new ArrayList<>();

        for (TaxTransactionDto row : rows) {
            TaxTransactionDto transaction =
                    TaxTransactionDto.builder()
                            .taxInputId(taxInputId)
                            .transactionType(row.getTransactionType())
                            .transactionDate(row.getTransactionDate())
                            .description(row.getDescription())
                            .category(row.getCategory())
                            .preTaxIncome(row.getPreTaxIncome())
                            .deductedTaxRate(row.getDeductedTaxRate())
                            .amount(row.getAmount())
                            .build();

            taxMapper.insertTransaction(transaction);

            saved.add(transaction);
        }

        invalidateAfterTransactionChange(taxInputId);

        return saved;
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

        // 실제 변경이 없으면 결과 삭제와 수정 시각 갱신을 생략한다.
        if (Objects.equals(existing.getCategory(), category)) {
            return;
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

        TaxResultRow row = taxMapper.findResult(taxInputId);

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

            return TaxResultResponse.builder()
                    .taxInputId(row.getTaxInputId())
                    .resultDetail(detail)
                    .calculatedAt(
                            row.getCalculatedAt()
                                    .atZone(SEOUL)
                                    .toOffsetDateTime()
                                    .toString()
                    )
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

        /*
         * 실제 계산 엔진과 연도별 계산 규칙이 연결되기 전에는
         * 임의의 세액이나 성공 결과를 저장하지 않는다.
         */
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
        /*
         * 분석번호를 전달한 경우에만 해당 분석과 업로드 로그를 연결한다.
         * 권한 없는 분석번호를 로그에 연결하지 않는다.
         */
        if (taxInputId != null) {
            requireInput(access, taxInputId, false);
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

            writeProcessLog(
                    taxInputId,
                    userId(access),
                    "UPLOAD",
                    "SUCCESS",
                    null
            );

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

    private TaxInputRequest normalizeInput(
            TaxInputRequest source,
            Long taxInputId,
            Long ownerId
    ) {
        if (source == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "분석 기본정보가 필요합니다.",
                    Map.of("input", "분석 기본정보를 전달해주세요.")
            );
        }

        /*
         * @Builder.Default를 사용하지 않고
         * 생략 가능한 입력의 기본값을 서비스에서 명시한다.
         *
         * 수정도 전체 수정 방식이므로 생략한 값에 기본값을 적용한다.
         */
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

        TaxTransactionDto transaction =
                taxMapper.findTransaction(
                        taxInputId,
                        transactionId
                );

        if (transaction == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "TRANSACTION_NOT_FOUND",
                    "해당 분석의 거래를 찾을 수 없습니다."
            );
        }

        return transaction;
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
                    left.getPreTaxIncome(),
                    right.getPreTaxIncome()
            ) || !sameDecimal(
                    left.getDeductedTaxRate(),
                    right.getDeductedTaxRate()
            ) || !sameDecimal(
                    left.getAmount(),
                    right.getAmount()
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

        // 100과 100.00은 동일한 금액으로 비교한다.
        return left.compareTo(right) == 0;
    }

    private Long userId(AccessContext access) {
        return access == null
                ? null
                : access.getUserId();
    }

    private String failureCode(Exception exception) {
        if (exception instanceof ApiException apiException) {
            return apiException.getCode();
        }

        if (exception instanceof IOException) {
            return "PDF_IO_ERROR";
        }

        return "PDF_PROCESSING_FAILED";
    }

    private TransactionTemplate logTransaction() {
        TransactionTemplate template =
                new TransactionTemplate(transactionManager);

        template.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );

        return template;
    }

    private void writeProcessLog(
            Long taxInputId,
            Long userId,
            String processType,
            String status,
            String failReason
    ) {
        try {
            logTransaction().executeWithoutResult(transactionStatus ->
                    taxMapper.insertProcessLog(
                            taxInputId,
                            userId,
                            processType,
                            status,
                            failReason
                    )
            );

        } catch (RuntimeException firstException) {
            /*
             * 분석이나 회원이 동시에 삭제된 경우를 고려해
             * 연결번호 없이 한 번 더 저장한다.
             */
            try {
                logTransaction().executeWithoutResult(transactionStatus ->
                        taxMapper.insertProcessLog(
                                null,
                                null,
                                processType,
                                status,
                                failReason
                        )
                );

                log.warn(
                        "처리 이력을 연결번호 없이 저장했습니다. type={}, status={}",
                        processType,
                        status
                );

            } catch (RuntimeException retryException) {
                /*
                 * DB 자체가 중단되면 DB 로그 저장은 보장할 수 없다.
                 * 서버 로그에 기록하고 원래 작업의 예외는 유지한다.
                 */
                log.error(
                        "처리 이력 저장 실패. type={}, status={}, reason={}",
                        processType,
                        status,
                        failReason,
                        retryException
                );
            }
        }
    }
}