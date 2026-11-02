package com.kh.back.taxupload.service;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.exception.ApiException;
import com.kh.back.taxupload.dto.PageOcrResultDto;
import com.kh.back.taxupload.dto.PdfParseResponseDto;
import com.kh.back.taxupload.dto.TaxServiceResponse;
import com.kh.back.taxupload.dto.TaxTransactionDto;
import com.kh.back.taxupload.dto.TaxTransactionDto.AccessContext;
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
     * 분석 기본정보 생성과 거래 최초 저장을 처리한다.
     * 기본정보와 거래를 함께 전달하면 하나의 트랜잭션에서 저장한다.
     */
    @Override
    @Transactional
    public TaxServiceResponse save( // 분석 기본정보를 만들고 거래를 처음 db에 저장하는 기능
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            TaxServiceRequest request
    ) {
        if (request == null) { // 저장 요청이 없으면 거절
            throw invalid(
                    "request",
                    "저장 요청이 필요합니다."
            );
        }

        AccessContext access = createAccess(  // 로그인 사용자와 비회원에게 허용된 분석번호를 이용해 접근 권한 정보를 구성
                principal,
                guestInputIds
        );

        Long taxInputId = request.getTaxInputId(); // 프론트가 보낸 요청에서 분석번호를 꺼냄, 분석번호를 보내지 않으면 null

        List<TaxTransactionDto> rows = // 요청에서 저장할 거래 목록을 꺼내 rows에 담음,
                request.getTransactions();

        boolean createAnalysis = taxInputId == null; // 분석번호가 없는지 검사

        // 기존 분석의 기본정보 변경은 update에서 처리한다.
        if (!createAnalysis && request.getInput() != null) { // 신규생성이 아니라 기존 분석번호를 사용하는 경우
            throw invalid( // 요청에 사업자등록 여부,나이, 기부금 등의 기본정보가 포함되어 있다는 뜻
                    "input",
                    "기존 분석의 기본정보는 수정 API를 이용해주세요."
            );
        }

        // 기존 분석에 저장할 때는 거래 목록이 필요하다.
        if (!createAnalysis && rows == null) {
            throw invalid(
                    "transactions",
                    "저장할 거래 목록이 필요합니다."
            );
        }  // !createAnalysis 기존 분석번호를 사용하는 경우 , // rows == null  요청에 거래 목록이 없는 경우 , 두조건이 모두 맞으면 오류 반환

        if (rows != null) {
            taxValidator.validateTransactionRows(rows);
        } // 목록이 저장 가능한 형식인지 검사하는 단계이며 정확한 검사 항목은 validateTransactionRows() 내부에 있음

        TaxInputRequest input;

        if (createAnalysis) { // 분석번호가 없어 새분석을 생성해야 할떄만 실행
            // 새 분석의 소유자는 요청 본문 대신 인증 정보에서 결정한다.
            input = normalizeInput( // request.getInput() 프론트가 보낸 기본정보,
                    request.getInput(),
                    null,   // null 신규 분석이므로 아직 분석번호가 없음,
                    access.getUserId() // 서버가 인증 정보로 확인한 사용자 번호, 비회원이면 null 일수있음
            );

            taxValidator.validateInput(input); // 정리한 기본정보가 유효한지 검사, 문제가 있으면 예외를 발생 시켜 저장 진행을 막음

            // 정식 저장에는 로그인이 필요하다.
            if ("SAVED".equals(input.getSaveStatus())) { // 새 분석이면 db에 생성하고 , 기존 분석이면 권한을 확인하며 조회하는 부분
                input.setUserId(
                        taxValidator.requireLogin(access)
                );
            }

            taxMapper.insertInput(input); // 새 분석 기본정보를 db에 insert

            taxInputId = input.getTaxInputId(); // db에서 생성된 분석번호를 꺼냄, 매퍼의 생성 키 설정으로 input 에 번호가 채워지는 구조
            //  반면 기존 분석번호가 있으면 else가 실행됨
        } else {
            // 기존 분석에 거래를 저장하기 전에 접근 권한과 잠금을 확인한다.
            input = requireInput(
                    access,
                    taxInputId,
                    true
            );
        }

        List<TaxTransactionDto> savedRows = List.of(); // 최종 반환할 저장결과를 우선 빈 목록으로 초기화, 이후 저장 결과가 생기면 다른 목록 대입

        if (rows != null) { // 프론트가 거래 목록을 보냈을떄 만 처리
            List<TaxTransactionDto> requested = // 검증이 끝난 저장 대상 거래를 모을 목록을 만듬
                    new ArrayList<>();

            for (TaxTransactionDto source : rows) { // 받은 거래를 하나씩 꺼냄, source는 현재 검사할 원본 거래
                taxValidator.validateTransactionSource( // 해당 거래가 요청한 분석에 저장 가능한지 검사 , 정확한 검사 항목은 이 함수 내부에 정의되있음
                        source,
                        taxInputId
                );

                taxValidator.validateCashTransaction(source); // 실제 입출금 거래로 저장 가능한지 검사, 공제대상금액이나 검산용 합계를 실제 거래 금액으로 저장하지 않도록 확인하는 역할

                TaxTransactionDto row =  // 프론트가 보낸 거래에서 db에 저장할 필드를 복사, 서버가 확인한 분석번호를 연결한 새 객체 생성
                        copyStoredTransaction(
                                source,
                                taxInputId
                        );

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
                    taxMapper.findTransactions(
                            taxInputId,
                            null
                    );

            // 같은 저장 요청을 재전송하면 기존 거래를 반환한다.
            if (sameTransactions(existing, requested)) {
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

                for (TaxTransactionDto row : requested) {
                    taxMapper.insertTransaction(row);
                }

                if (!requested.isEmpty()) {
                    invalidateAfterTransactionChange(
                            taxInputId
                    );
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
     * input이 있으면 기본정보를 수정한다.
     * transactionId와 category가 있으면 카테고리를 수정한다.
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

        // 수정할 분석은 URL에서 결정한다. 본문과 경로가 다르면 거절한다.
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

        // 카테고리 수정에는 거래번호와 카테고리가 모두 필요하다.
        if (updateCategory) {
            taxValidator.validateId(
                    request.getTransactionId(),
                    "transactionId"
            );

            taxValidator.validateCategory(
                    request.getCategory()
            );
        }

        AccessContext access = createAccess(
                principal,
                guestInputIds
        );

        // 두 수정 작업이 같은 분석을 기준으로 처리되도록 먼저 잠근다.
        TaxInputRequest existingInput = requireInput(
                access,
                taxInputId,
                true
        );

        TaxInputRequest updatedInput = existingInput;

        if (updateProfile) {
            updatedInput = normalizeInput(
                    request.getInput(),
                    taxInputId,
                    existingInput.getUserId()
            );

            taxValidator.validateInput(updatedInput);

            // 직접 만든 비회원 분석만 현재 회원 소유로 전환할 수 있다.
            if (existingInput.getUserId() == null
                    && "SAVED".equals(updatedInput.getSaveStatus())) {

                Long loginUserId =
                        taxValidator.requireLogin(access);

                if (!access.getGuestInputIds().contains(taxInputId)) {
                    throw new ApiException(
                            HttpStatus.FORBIDDEN,
                            "ACCESS_DENIED",
                            "직접 생성한 비회원 분석만 저장할 수 있습니다."
                    );
                }

                updatedInput.setUserId(loginUserId);
            }
        }

        TaxTransactionDto existingTransaction = null;
        boolean categoryChanged = false;

        if (updateCategory) {
            // 다른 분석의 거래를 수정하지 못하도록 분석번호와 함께 조회한다.
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
                    Objects.equals(
                            category,
                            after.getCategory()
                    )
            );
        }

        // 기본정보와 카테고리를 함께 바꿔도 결과 무효화는 한 번만 수행한다.
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

        // 경로와 본문에 서로 다른 분석번호가 전달되면 거절한다.
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

        // 전체 삭제와 개별 거래 삭제를 동시에 요청하지 못하도록 한다.
        if (deleteAll && transactionId != null) {
            throw invalid(
                    "transactionId",
                    "분석 전체 삭제에는 거래번호를 전달할 수 없습니다."
            );
        }

        // 전체 삭제를 명시하지 않았다면 거래번호가 반드시 필요하다.
        if (!deleteAll) {
            taxValidator.validateId(
                    transactionId,
                    "transactionId"
            );
        }

        AccessContext access = createAccess(
                principal,
                guestInputIds
        );

        // 삭제 전에 분석 접근 권한을 확인하고 부모 분석을 잠근다.
        requireInput(
                access,
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

        // 지정한 거래가 해당 분석에 속하는지 확인한다.
        requireTransaction(
                taxInputId,
                transactionId
        );

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

        // 거래 변경으로 이전 계산 결과를 무효화한다.
        invalidateAfterTransactionChange(taxInputId);
    }

    /**
     * PDF에서 거래 내역을 추출한다.
     * 추출한 거래는 이 메서드에서 DB에 저장하지 않는다.
     */
    @Override
    public PdfParseResponseDto parse(
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            Long taxInputId,
            MultipartFile file
    ) throws IOException {
        AccessContext access = createAccess(
                principal,
                guestInputIds
        );

        if (taxInputId != null) {
            requireInput(
                    access,
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

            // PDF 추출 결과에는 저장 완료 상태를 부여하지 않는다.
            response.setTransactions(transactions);
            response.setSavedCount(0);
            response.setPersisted(false);
            response.setTaxInputIdsByYear(
                    new LinkedHashMap<>()
            );

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

            // 추출 데이터에 포함된 DB 식별자는 사용하지 않는다.
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

                row = copyStoredTransaction(
                        source,
                        null
                );

                if (source.getActualAmount() != null) {
                    row.setAmount(source.getActualAmount());
                }

                taxValidator.validateStoredTransaction(row);
                taxValidator.validateTransactions(List.of(row));

            } catch (ApiException exception) {
                String reason = exception.getMessage();

                // 검증 실패 거래도 버리지 않고 검토 대상으로 반환한다.
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

        // 저장에 필요한 필드만 새 객체에 복사한다.
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

        taxValidator.requireAccess(
                access,
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

        // 로그인 이후에도 직접 만든 비회원 분석의 소유권 전환에 사용한다.
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