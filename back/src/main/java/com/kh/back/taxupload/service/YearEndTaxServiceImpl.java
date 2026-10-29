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

    private final ObjectMapper objectMapper = new ObjectMapper(); // db에 저장된 결과 json을 java 객체로 해석할때 사용

    /**
     * 새 분석 기본정보를 생성한다.
     */
    @Override
    @Transactional
    public TaxInputRequest save( // 분석 기본정보 생성 , 새로운 분석 기본정보 한건 저장
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            TaxInputRequest request
    ) {
        AccessContext access = createAccess(principal, guestInputIds); // 접근 정보를 만듬

        // 소유자는 요청 본문 대신 인증 정보에서 결정한다.
        TaxInputRequest input = normalizeInput( // 저장할 기본정보 객체 생성
                request,
                null,
                access.getUserId()
        );

        taxValidator.validateInput(input); // 연도 , 기부금 , 저장 상태등을 검사

        // 비회원은 TEMP로 생성할 수 있고 SAVED에는 로그인이 필요하다.
        if ("SAVED".equals(input.getSaveStatus())) {
            input.setUserId(taxValidator.requireLogin(access));
        }

        taxMapper.insertInput(input); // db에 저장한뒤 생성된 분석을 반환 , mapper 의 생성 키 설정에 따라 분석번호도 input으로 채워짐

        return input;
    }

    /**
     * 분석·저장 목록·거래 목록·계산 결과 조회를 통합한다.
     */
    @Override
    @Transactional(readOnly = true)
    public TaxServiceResponse get(  // 여러 조회 기능 통합
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            TaxServiceRequest request
    ) {
        if (request == null) {  // 먼저 요청이 있는지 검사
            throw invalid("request", "조회 요청이 필요합니다.");
        }

        String target = request.getTarget();   // 어떤 데이터를 조회할지는 request.target으로 구분

        // 잘못된 구분값은 다른 조회로 해석하지 않고 거절한다.
        if (!"INPUT".equals(target)    // 지원하는 구분값인지 검사
                && !"SAVED_INPUTS".equals(target)
                && !"TRANSACTIONS".equals(target)
                && !"RESULT".equals(target)) {  //  네 값중 어느것도 해당하지 않으면 오류 반환
            throw invalid("target", "지원하지 않는 조회 대상입니다.");
        }   // "Input".equals (target) 형태이므로 target 이 null 이어도 비교 오류가 나지 않음

        AccessContext access = createAccess(principal, guestInputIds);

        // 저장 목록 조회는 회원 전용이며 특정 분석번호를 요구하지 않는다.
        if ("SAVED_INPUTS".equals(target)) { // 이 조회는 로그인이 필요하고 특정 분석번호는 필요하지 않는다.
            Long loginUserId = taxValidator.requireLogin(access);

            if (request.getTaxYear() != null) { // 만약 귀속연도가 null 이 아니면 taxvalidator에서 귀속연도 검증
                taxValidator.validateTaxYear(request.getTaxYear());
            }

            return TaxServiceResponse.builder() // 응답은 builder로 만듬
                    .inputs(
                            taxMapper.findSavedInputs( // 연도가 전달됐다고 검사하고 목록 조회
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
            return TaxServiceResponse.builder() // 분석 기본정보
                    .input(input)
                    .build();
        }

        if ("TRANSACTIONS".equals(target)) {
            return TaxServiceResponse.builder() // 거래 목록
                    .transactions(
                            taxMapper.findTransactions(taxInputId, null)
                    )
                    .build();
        }

        return TaxServiceResponse.builder() // 계산 결과
                .result(readStoredResult(taxInputId))
                .build();
    }

    /**
     * 거래 목록을 최초 저장한다.
     * 기존 거래 전체를 새 목록으로 교체하지 않는다.
     */
    @Override
    @Transactional
    public List<TaxTransactionDto> transaction( // 거래 목록 최초 저장
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            Long taxInputId,
            List<TaxTransactionDto> rows
    ) {
        AccessContext access = createAccess(principal, guestInputIds);

        TaxInputRequest input =  // 먼저 분석을 확인하고 잠금
                requireInput(access, taxInputId, true);

        taxValidator.validateTransactionRows(rows); // 목록이 전달됐는지 확인

        List<TaxTransactionDto> requested = new ArrayList<>(); // 검증후 저장할 거래를 담을 목록을 만듬

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
            taxValidator.validateTransactions(requested); // 목록 정리가 끝나면 상세 검사를 수행
        }

        List<TaxTransactionDto> existing =
                taxMapper.findTransactions(taxInputId, null); // 기존 db거래는 다음 코드로 조회

                                          // 동일한 저장 재요청은 INSERT하지 않고 기존 목록을 반환한다.
        if (sameTransactions(existing, requested)) {
            return existing;
        }

        if (!existing.isEmpty()) { // 기존 거래가 있는데 다른 목록이 전달되면 전체 교체를 거절
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "USE_TRANSACTION_CHANGE_API",
                    "기존 거래는 개별 수정·삭제 API로 변경해주세요."
            );
        }

        taxValidator.validateNewTransactionIds(rows);  // 신규 저장 가능한 경우 기존 거래번호가 없는지 검사한뒤

        for (TaxTransactionDto row : requested) {  // 한건씩 insert , 저장 후에는 이전 계산 결과를 무효화하고 , 저장된 목록을 조회해 반환
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
    public TaxInputRequest update( // 기본정보, 카테고리 수정 통합
            CustomUserDetails principal,
            Set<Long> guestInputIds,
            TaxServiceRequest request
    ) {
        if (request == null) { // 수정 요청 객체가 없으면 예외를 발생시켜 중단
            throw invalid("request", "수정 요청이 필요합니다.");
        }

        String target = request.getTarget(); // 수정 대상 확인

        if (!"INPUT".equals(target) // 만약 input도 아니고 category도 아니면 거절
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
            ); // 기본정보 수정인데 거래번호 , 카테고리가 함께 들어오면 오류
        }

        if ("CATEGORY".equals(target)) {  // 카테고리 수정에 필요한 값 검사
            taxValidator.validateId(  // 요청에서 거래번호를 꺼내 값이 있고 0보다 큰지 검사합니다. 잘못됐으면 transactionId 필드 오류를 발생시킴
                    request.getTransactionId(),
                    "transactionId"
            );
            taxValidator.validateCategory(request.getCategory()); // 요청에서 카테고리를 꺼내 대문자로 시작하는 50자 이하 코드 형식인지 검사
            if (request.getInput() != null) { // 만약 요청에 분석 기본정보 객체 까지 함께 들어왔는지 확인 , != null 은 값이 있다 는 뜻
                throw invalid( // 카테고리만 수정하는 요청인데 분석 기본정보도 들어왔다면 입력 오류를 발생시키고 처리를 중단 시킴
                        "input",
                        "카테고리 수정에는 분석 기본정보를 전달할 수 없습니다."
                );
            }
        }

        AccessContext access = createAccess(principal, guestInputIds); // 회원 인증 정보인 principal 과 서버 세션의 비회원 분석 번호목록인
                                                                       // guestInputIds를 합쳐, 접근 권한 검사에 사용할 객체 access를 만듬.
        Long taxInputId = request.getTaxInputId();
        // 요청객체 request 에서 수정할 분석번호를 꺼내 taxinputid 변수에 담음

        // 두 수정 작업 모두 부모 분석의 권한 확인과 잠금을 먼저 수행한다.
        TaxInputRequest existingInput = // 해당 분석을 조회하고 접근 권한을 확인한뒤 , 기존 분석정보를 existingInput 에 담는 부분
                requireInput(access, taxInputId, true);

        if ("INPUT".equals(target)) { // 수정 대상이 input 분석 기본정보일 때만 이 코드를 실행
            TaxInputRequest updated = normalizeInput( //  normalizeInput()은 기본정보를 복사하고, 누락된 부양가족 수·기부금·저장 상태에 기본값을 넣고, 분석번호와 소유자는 서버에서 확인한 값을 사용
                    // 수정에 사용할 새 분석정보 객체를 만들어 updated 에 담음
                    request.getInput(), // 요청에서 꺼낸 새로운 기본 정보
                    taxInputId, // 수정할 분석번호
                    existingInput.getUserId() // 기존 분석의 소유 회원번호
            );

            taxValidator.validateInput(updated); // 기본정보 수정 요청이면 , 수정할 객체를 정리해서 만들고 그 값이 올바른지 검사한다

            // 직접 만든 비회원 분석만 현재 회원 소유로 전환할 수 있다.
            if (existingInput.getUserId() == null // 비회원 분석을 회원 소유로 저장하는 조건을 확인하고 기본정보를 db에 수정하는 부분
                    && "SAVED".equals(updated.getSaveStatus())) { // 다음 두 조건을 만족할때 안쪽 코드를 실행
                Long loginUserId = taxValidator.requireLogin(access); // 현재 로그인 했는지 확인하고 회원번호를 가져옴, 비회원 상태라면 예외가 발생해 처리가 중단됨

                if (!access.getGuestInputIds().contains(taxInputId)) {
                    throw new ApiException( // 만약 직접 만든 비회원 분석이 아니라면  403 접근 거절 예외 발생
                            HttpStatus.FORBIDDEN,
                            "ACCESS_DENIED",
                            "직접 생성한 비회원 분석만 저장할 수 있습니다."
                    );
                }

                updated.setUserId(loginUserId); // 검사를 통과하면 수정할 분석의 소유자를 현재 로그인한 회원으로 설정 , java 객체만 변경
            }

            taxMapper.updateInput(updated); // 정리한 기본정보를 실제 db 의 tax_inputs 에 반영 , 위의 비회원 전환 조건에 해당하지 않는 일반 기본정보수정도 이 줄을 실행
            taxMapper.deleteResult(taxInputId); // 해당 분석의 이전 계싼 결과를 삭제 , 입력 조건이 바뀌었기 때문이고 거래내역 삭제 X

            return updated;
        }

        Long transactionId = request.getTransactionId(); // 요청에서 수정할 거래번호를 꺼낸 transactionId에 담음
        String category = request.getCategory(); // 요청에서 새로 적용할 카테고리를 꺼내 category에 담음

        TaxTransactionDto existing = // 해당 분석에 속한 거래를 db에서 조회하고, 기존 거래정보를 existing에 담음
                requireTransaction(taxInputId, transactionId); // requireTransaction(거래 번호가 유효한지 검사, 분석번호 + 거래번호로 조회 , 거래가 없으면 오류 -> 있으면 기존 객체 반환)

        log.debug(
                "카테고리 수정 요청: 분석번호={}, 거래번호={}, 기존값={}, 요청값={}",
                taxInputId, // 거래가 속한 분석번호
                transactionId, // 수정할 거래번호
                existing.getCategory(), // 기존값( db에서 조회한 기존 카테고리)
                category    // 요청값(카테고리)
        );

        // 기존값과 같으면 SQL 수정과 후처리를 생략한다.
        if (Objects.equals(existing.getCategory(), category)) { // db의 기존 카테고리와 요청한 새 카테고리가 같은지 비교
            log.debug(
                    "카테고리 수정 생략: 거래번호={}, 기존값과 요청값이 같음",
                    transactionId
            ); // Objects.equals() : 두 값을 null 에도 안전하게 비교

            return existingInput; // 두 값이 같으면 수정 생략 으로 반환
        }

        int updated = taxMapper.updateCategory( // 지정한 거래 한건의 카테고리를 db에서 수정하고, 영향받은 행 수를 updated 에 담는 부분
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

        if (updated != 1) {  // 수정 행 수를 확인 하고 변경값을 다시 조회해 로그를 남긴뒤 후처리 하는 부분
            throw new ApiException( // 앞에서 실행한 카테고리 update 의 영향 행 수가 1이 아닌지 확인
                    HttpStatus.CONFLICT, // 정확히 한건이 아니면 409 conflict 예외처리 , 해당건 롤백
                    "TRANSACTION_UPDATE_FAILED",
                    "거래 수정 결과를 확인하지 못했습니다."
            );
        }

        TaxTransactionDto after = // 수정한 거래를 db에서 다시 조회해 after 에 담음
                requireTransaction(taxInputId, transactionId); // existing :수정전 거래정보 , after: 수정후 재조회한 거래정보

        log.debug(
                "카테고리 DB 재조회: 거래번호={}, 변경전={}, 요청값={}, DB조회값={}, 일치여부={}",
                transactionId, // 수정한 거래번호
                existing.getCategory(), // 수정 전 카테고리
                category, // 요청한 새 카테고리
                after.getCategory(), // 수정후 db에서 다시 조회한 카테고리
                Objects.equals(category, after.getCategory()) // 요청값과 재조회 값이 같으면 true, 다르면 false
        );

        invalidateAfterTransactionChange(taxInputId); // 거래가 변경됐으므로, 이전 계산 결과를 삭제하고 분석수정 시각을 갱신

        return existingInput; // 해당 거래가 속한 분석 기본정보를 반환하고 메서드 종료
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