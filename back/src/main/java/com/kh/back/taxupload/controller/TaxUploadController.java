package com.kh.back.taxupload.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.taxupload.dto.PdfParseResponseDto;
import com.kh.back.taxupload.dto.TaxServiceResponse;
import com.kh.back.taxupload.dto.TaxTransactionDto;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxInputRequest;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxServiceRequest;
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

    /**
     * 분석 기본정보와 거래 목록을 저장한다.
     */
    @PostMapping("/save")
    public ResponseEntity<ApiResponse<TaxServiceResponse>> save(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @RequestBody TaxServiceRequest request
    ) {
        boolean createAnalysis =
                request.getTaxInputId() == null;

        // 새 비회원 분석의 소유권을 기록할 세션을 준비한다.
        HttpSession guestSession =
                principal == null && createAnalysis
                        ? servletRequest.getSession(true)
                        : servletRequest.getSession(false);

        TaxServiceResponse saved = service.save(
                principal,
                readGuestInputs(guestSession),
                request
        );

        TaxInputRequest input = saved.getInput();

        // 서비스 저장이 성공한 뒤 비회원 소유 분석번호를 기록한다.
        if (createAnalysis && input.getUserId() == null) {
            changeGuestInput(
                    guestSession,
                    input.getTaxInputId(),
                    true
            );
        }

        HttpStatus status = createAnalysis
                ? HttpStatus.CREATED
                : HttpStatus.OK;

        return ResponseEntity
                .status(status)
                .body(ApiResponse.ok(saved));
    }
    /**
     * 기본정보와 거래 카테고리를 한 번에 수정한다.
     */
    @PostMapping("/inputs/{taxInputId}/update")
    public ApiResponse<TaxInputRequest> update(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @PathVariable("taxInputId") Long taxInputId,
            @RequestBody TaxServiceRequest request
    ) {
        TaxInputRequest updated = service.update(
                principal,
                readGuestInputs(
                        servletRequest.getSession(false)
                ),
                taxInputId,
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

    /**
     * 분석 전체 삭제와 거래 한 건 삭제를 하나의 API로 처리한다.
     */
    @PostMapping("/inputs/{taxInputId}/delete")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @PathVariable("taxInputId") Long taxInputId,
            @RequestParam(
                    value = "deleteAll",
                    defaultValue = "false"
            ) boolean deleteAll,
            @RequestBody TaxServiceRequest request
    ) {
        service.delete(
                principal,
                readGuestInputs(
                        servletRequest.getSession(false)
                ),
                taxInputId,
                request,
                deleteAll
        );

        // 분석 전체 삭제가 성공했을 때만 세션의 분석번호를 제거한다.
        if (deleteAll) {
            changeGuestInput(
                    servletRequest.getSession(false),
                    taxInputId,
                    false
            );
        }

        return ResponseEntity.noContent().build();
    }
    /**
     * PDF에서 거래 내역을 추출한다.
     */
    @PostMapping(
            value = "/parse",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse<PdfParseResponseDto> parse(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest servletRequest,
            @RequestParam(value = "taxInputId", required = false)
            Long taxInputId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        if (principal == null) {
            servletRequest.getSession(true);
        }

        // 추출만 수행하며 DB 저장은 save 요청에서 처리한다.
        PdfParseResponseDto response = service.parse(
                principal,
                readGuestInputs(
                        servletRequest.getSession(false)
                ),
                taxInputId,
                file
        );

        return ApiResponse.ok(response);
    }

    private Set<Long> readGuestInputs(
            HttpSession session
    ) {
        if (session == null) {
            return Set.of();
        }

        synchronized (session) {
            Object value = session.getAttribute(
                    GUEST_INPUT_IDS
            );

            Set<Long> ids = new HashSet<>();

            if (value instanceof Set<?> values) {
                for (Object item : values) {
                    if (item instanceof Long id && id > 0) {
                        ids.add(id);
                    }
                }
            }

            // 서비스에는 세션 원본 대신 복사본을 전달한다.
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

        // 같은 세션의 분석번호 목록이 동시에 변경되지 않도록 한다.
        synchronized (session) {
            Set<Long> ids = new HashSet<>(
                    readGuestInputs(session)
            );

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