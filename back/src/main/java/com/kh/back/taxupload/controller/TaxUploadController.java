package com.kh.back.taxupload.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.taxupload.dto.PdfParseResponseDto;
import com.kh.back.taxupload.dto.TaxTransactionDto;
import com.kh.back.taxupload.dto.TaxTransactionDto.CategoryRequest;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxInputRequest;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxResultResponse;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxServiceRequest;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxServiceResponse;
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
        // 비회원 분석의 소유권을 기록할 서버 세션을 준비한다.
        HttpSession guestSession = principal == null
                ? servletRequest.getSession(true)
                : null;

        TaxInputRequest created = service.save(
                principal,
                readGuestInputs(servletRequest.getSession(false)),
                request
        );

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
        TaxServiceRequest request = TaxServiceRequest.builder()
                .target("SAVED_INPUTS")
                .taxYear(taxYear)
                .build();

        TaxServiceResponse response = service.get(
                principal,
                readGuestInputs(servletRequest.getSession(false)),
                request
        );

        // 필요한 값만 꺼내 기존 목록 응답 형식을 유지한다.
        return ApiResponse.ok(response.getInputs());
    }

    @PostMapping("/inputs/{taxInputId}/detail")
    public ApiResponse<TaxInputRequest> getInput(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @PathVariable("taxInputId") Long taxInputId
    ) {
        TaxServiceRequest request = TaxServiceRequest.builder()
                .target("INPUT")
                .taxInputId(taxInputId)
                .build();

        TaxServiceResponse response = service.get(
                principal,
                readGuestInputs(servletRequest.getSession(false)),
                request
        );

        return ApiResponse.ok(response.getInput());
    }

    @PostMapping("/inputs/{taxInputId}/update")
    public ApiResponse<TaxInputRequest> updateInput(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @PathVariable("taxInputId") Long taxInputId,
            @RequestBody TaxInputRequest input
    ) {
        // 수정 대상은 요청 본문이 아니라 Controller 경로에서 결정한다.
        TaxServiceRequest request = TaxServiceRequest.builder()
                .target("INPUT")
                .taxInputId(taxInputId)
                .input(input)
                .build();

        TaxInputRequest updated = service.update(
                principal,
                readGuestInputs(servletRequest.getSession(false)),
                request
        );

        // 회원 소유로 전환된 분석은 비회원 권한 목록에서 제거한다.
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
        TaxServiceRequest request = TaxServiceRequest.builder()
                .target("INPUT")
                .taxInputId(taxInputId)
                .build();

        service.delete(
                principal,
                readGuestInputs(servletRequest.getSession(false)),
                request
        );

        changeGuestInput(
                servletRequest.getSession(false),
                taxInputId,
                false
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/inputs/{taxInputId}/transactions/save")
    public ApiResponse<List<TaxTransactionDto>> saveTransactions(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @PathVariable("taxInputId") Long taxInputId,
            @RequestBody List<TaxTransactionDto> rows
    ) {
        return ApiResponse.ok(
                service.transaction(
                        principal,
                        readGuestInputs(servletRequest.getSession(false)),
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
        TaxServiceRequest request = TaxServiceRequest.builder()
                .target("TRANSACTIONS")
                .taxInputId(taxInputId)
                .build();

        TaxServiceResponse response = service.get(
                principal,
                readGuestInputs(servletRequest.getSession(false)),
                request
        );

        return ApiResponse.ok(response.getTransactions());
    }

    @PostMapping(
            "/inputs/{taxInputId}/transactions/{transactionId}/category/update"
    )
    public ResponseEntity<Void> updateCategory(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @PathVariable("taxInputId") Long taxInputId,
            @PathVariable("transactionId") Long transactionId,
            @RequestBody CategoryRequest categoryRequest
    ) {
        TaxServiceRequest request = TaxServiceRequest.builder()
                .target("CATEGORY")
                .taxInputId(taxInputId)
                .transactionId(transactionId)
                .category(categoryRequest.getCategory())
                .build();

        service.update(
                principal,
                readGuestInputs(servletRequest.getSession(false)),
                request
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
        // 거래 삭제는 TRANSACTION을 명시하고 거래번호를 반드시 전달한다.
        TaxServiceRequest request = TaxServiceRequest.builder()
                .target("TRANSACTION")
                .taxInputId(taxInputId)
                .transactionId(transactionId)
                .build();

        service.delete(
                principal,
                readGuestInputs(servletRequest.getSession(false)),
                request
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/inputs/{taxInputId}/results/detail")
    public ApiResponse<TaxResultResponse> getResult(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @PathVariable("taxInputId") Long taxInputId
    ) {
        TaxServiceRequest request = TaxServiceRequest.builder()
                .target("RESULT")
                .taxInputId(taxInputId)
                .build();

        TaxServiceResponse response = service.get(
                principal,
                readGuestInputs(servletRequest.getSession(false)),
                request
        );

        return ApiResponse.ok(response.getResult());
    }

    @PostMapping("/inputs/{taxInputId}/calculate")
    public ApiResponse<TaxResultResponse> calculateInput(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @PathVariable("taxInputId") Long taxInputId
    ) {
        return ApiResponse.ok(
                service.calculateInput(
                        principal,
                        readGuestInputs(servletRequest.getSession(false)),
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
        if (principal == null) {
            servletRequest.getSession(true);
        }

        // PDF 추출만 수행하며 비회원 분석번호 등록은 생성 API가 처리한다.
        PdfParseResponseDto response = service.parsePdf(
                principal,
                readGuestInputs(servletRequest.getSession(false)),
                taxInputId,
                file
        );

        return ApiResponse.ok(response);
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

            // 서비스에는 서버 세션에서 읽은 복사본만 전달한다.
            return Set.copyOf(ids);
        }
    }

    private void changeGuestInput(
            HttpSession session,
            Long taxInputId,
            boolean add
    ) {
        if (session == null) {
            return;
        }

        // 같은 세션의 분석번호 목록을 동시에 변경하지 않도록 처리한다.
        synchronized (session) {
            Set<Long> ids =
                    new HashSet<>(readGuestInputs(session));

            if (add) {
                ids.add(taxInputId);
            } else {
                ids.remove(taxInputId);
            }

            session.setAttribute(
                    GUEST_INPUT_IDS,
                    Set.copyOf(ids)
            );
        }
    }
}