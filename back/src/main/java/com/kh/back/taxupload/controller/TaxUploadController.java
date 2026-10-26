package com.kh.back.taxupload.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.taxupload.dto.PdfParseResponseDto;
import com.kh.back.taxupload.dto.TaxTransactionDto;
import com.kh.back.taxupload.dto.TaxTransactionDto.AccessContext;
import com.kh.back.taxupload.dto.TaxTransactionDto.CategoryRequest;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxInputRequest;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxResultResponse;
import com.kh.back.taxupload.service.YearEndTaxService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/tax")
@RequiredArgsConstructor
public class TaxUploadController {

    private static final String GUEST_INPUT_IDS =
            "taxupload.guestInputIds";

    private final YearEndTaxService service;

    @PostMapping("/inputs/create")
    public ResponseEntity<ApiResponse<TaxInputRequest>> createInput(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @RequestBody TaxInputRequest request
    ) {
        HttpSession guestSession = principal == null
                ? servletRequest.getSession(true)
                : null;

        TaxInputRequest created = service.createInput(
                access(principal, servletRequest),
                request
        );

        // 비회원이 생성한 분석번호만 해당 서버 세션에 등록한다.
        if (created.getUserId() == null) {
            changeGuestInput(
                    guestSession,
                    created.getTaxInputId(),
                    true
            );
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created));
    }

    @PostMapping("/inputs/list")
    public ApiResponse<List<TaxInputRequest>> getSavedInputs(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @RequestParam(value = "taxYear", required = false)
            Integer taxYear
    ) {
        return ApiResponse.ok(
                service.getSavedInputs(
                        access(principal, servletRequest),
                        taxYear
                )
        );
    }

    @PostMapping("/inputs/{taxInputId}/detail")
    public ApiResponse<TaxInputRequest> getInput(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @PathVariable("taxInputId") Long taxInputId
    ) {
        return ApiResponse.ok(
                service.getInput(
                        access(principal, servletRequest),
                        taxInputId
                )
        );
    }

    @PostMapping("/inputs/{taxInputId}/update")
    public ApiResponse<TaxInputRequest> updateInput(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @PathVariable("taxInputId") Long taxInputId,
            @RequestBody TaxInputRequest request
    ) {
        TaxInputRequest updated = service.updateInput(
                access(principal, servletRequest),
                taxInputId,
                request
        );

        if (updated.getUserId() != null) {
            changeGuestInput(
                    servletRequest.getSession(false),
                    taxInputId,
                    false
            );
        }

        return ApiResponse.ok(updated);
    }

    @PostMapping("/inputs/{taxInputId}/delete")
    public ResponseEntity<Void> deleteInput(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @PathVariable("taxInputId") Long taxInputId
    ) {
        service.deleteInput(
                access(principal, servletRequest),
                taxInputId
        );

        changeGuestInput(
                servletRequest.getSession(false),
                taxInputId,
                false
        );

        return ResponseEntity.noContent().build();
    }

    /*
     * 거래 전체 목록을 저장한다.
     * 일부 거래만 전달하면 나머지 기존 거래는 삭제된다.
     */
    @PostMapping("/inputs/{taxInputId}/transactions/save")
    public ApiResponse<List<TaxTransactionDto>> saveTransactions(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @PathVariable("taxInputId") Long taxInputId,
            @RequestBody List<TaxTransactionDto> rows
    ) {
        return ApiResponse.ok(
                service.saveTransactions(
                        access(principal, servletRequest),
                        taxInputId,
                        rows
                )
        );
    }

    @PostMapping("/inputs/{taxInputId}/transactions/list")
    public ApiResponse<List<TaxTransactionDto>> getTransactions(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @PathVariable("taxInputId") Long taxInputId
    ) {
        return ApiResponse.ok(
                service.getTransactions(
                        access(principal, servletRequest),
                        taxInputId
                )
        );
    }

    @PostMapping(
            "/inputs/{taxInputId}/transactions/{transactionId}/category/update"
    )
    public ResponseEntity<Void> updateCategory(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @PathVariable("taxInputId") Long taxInputId,
            @PathVariable("transactionId") Long transactionId,
            @RequestBody CategoryRequest request
    ) {
        service.updateCategory(
                access(principal, servletRequest),
                taxInputId,
                transactionId,
                request.getCategory()
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping(
            "/inputs/{taxInputId}/transactions/{transactionId}/delete"
    )
    public ResponseEntity<Void> deleteTransaction(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @PathVariable("taxInputId") Long taxInputId,
            @PathVariable("transactionId") Long transactionId
    ) {
        service.deleteTransaction(
                access(principal, servletRequest),
                taxInputId,
                transactionId
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/inputs/{taxInputId}/results/detail")
    public ApiResponse<TaxResultResponse> getResult(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @PathVariable("taxInputId") Long taxInputId
    ) {
        return ApiResponse.ok(
                service.getResult(
                        access(principal, servletRequest),
                        taxInputId
                )
        );
    }

    @PostMapping("/inputs/{taxInputId}/calculate")
    public ApiResponse<TaxResultResponse> calculateInput(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @PathVariable("taxInputId") Long taxInputId
    ) {
        return ApiResponse.ok(
                service.calculateInput(
                        access(principal, servletRequest),
                        taxInputId
                )
        );
    }

    @PostMapping(
            value = "/parse",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse<PdfParseResponseDto> parsePdf(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @RequestParam(value = "taxInputId", required = false)
            Long taxInputId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {

        // 비회원은 업로드 전에 서버 세션을 생성한다.
        HttpSession guestSession = principal == null
                ? servletRequest.getSession(true)
                : null;

        // 서비스에서 PDF 분석과 DB 저장을 완료한 뒤 응답을 받는다.
        PdfParseResponseDto response = service.parsePdf(
                access(principal, servletRequest),
                taxInputId,
                file
        );

        /*
         * 이번 업로드에서 서버가 생성한 분석번호를 비회원 세션에 등록한다.
         * 이후 같은 세션에서 저장된 거래를 조회·수정할 수 있게 한다.
         */
        if (guestSession != null
                && response.getTransactions() != null) {

            response.getTransactions().stream()
                    .filter(Objects::nonNull)
                    .map(TaxTransactionDto::getTaxInputId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .forEach(createdInputId ->
                            changeGuestInput(
                                    guestSession,
                                    createdInputId,
                                    true
                            )
                    );
        }

        return ApiResponse.ok(response);
    }

    private AccessContext access(
            CustomUserDetails principal,
            HttpServletRequest request
    ) {
        boolean admin = principal != null
                && principal.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(
                                authority.getAuthority()
                        )
                );

        return AccessContext.builder()
                .userId(
                        principal == null
                                ? null
                                : principal.getUserId()
                )
                .admin(admin)
                .guestInputIds(
                        readGuestInputs(request.getSession(false))
                )
                .build();
    }

    private Set<Long> readGuestInputs(HttpSession session) {
        if (session == null) {
            return Set.of();
        }

        synchronized (session) {
            Object value = session.getAttribute(GUEST_INPUT_IDS);

            Set<Long> ids = new HashSet<>();

            if (value instanceof Set<?> values) {
                for (Object item : values) {
                    if (item instanceof Long id && id > 0) {
                        ids.add(id);
                    }
                }
            }

            return Set.copyOf(ids);
        }
    }

    /*
     * 세션 목록 추가·삭제의 중복 코드를 한 메서드로 처리한다.
     */
    private void changeGuestInput(
            HttpSession session,
            Long taxInputId,
            boolean add       //ex: 비회원이 PDF를 올려 taxInputId = 7이 생성되면, 세션에 7을 등록,  이후 서버는 이 세션에서 만든 분석번호인가?를 확인해 조회·수정을 허용

    ) {
        if (session == null) {  // 만약 session 값이 없으면 ; 를 반환( 종료)
            return;
        }

        synchronized (session) { // synchronized 가 같은 세션 동시 변경을 막음
            Set<Long> ids =
                    new HashSet<>(readGuestInputs(session));

            if (add) {    // 만약 add가 true면 분석번호를 추가, false면 제거
                ids.add(taxInputId);
            } else {
                ids.remove(taxInputId);
            }

            session.setAttribute(
                    GUEST_INPUT_IDS,
                    Set.copyOf(ids)  //ids 에 3,7 이 있으면 , 세션에 접근 가능한
                                      // 분석번호 목록으로  3, 7을 저장
            );
        }
    }
}