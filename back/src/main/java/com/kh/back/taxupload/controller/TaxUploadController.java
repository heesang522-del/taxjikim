package com.kh.back.taxupload.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.taxupload.dto.PdfParseResponseDto;
import com.kh.back.taxupload.dto.TaxServiceResponse;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxInputRequest;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxServiceRequest;
import com.kh.back.taxupload.service.YearEndTaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
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

@RestController
@RequestMapping("/api/v1/tax")
@RequiredArgsConstructor
public class TaxUploadController {

    private final YearEndTaxService service;

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<TaxServiceResponse>> save(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody TaxServiceRequest request
    ) {
        boolean createAnalysis = request.getTaxInputId() == null;
        TaxServiceResponse saved = service.save(
                principal, request
        );
        HttpStatus status = createAnalysis ? HttpStatus.CREATED : HttpStatus.OK;

        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(saved));
    }

    @PostMapping("/inputs/{taxInputId}/update")
    public ApiResponse<TaxInputRequest> update(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable("taxInputId") Long taxInputId,
            @RequestBody TaxServiceRequest request
    ) {
        return ApiResponse.ok(
                service.update(principal, taxInputId, request)
        );
    }

    @PostMapping("/inputs/{taxInputId}/delete")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable("taxInputId") Long taxInputId,
            @RequestParam(value = "deleteAll", defaultValue = "false") boolean deleteAll,
            @RequestBody TaxServiceRequest request
    ) {
        service.delete(principal, taxInputId, request, deleteAll);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PdfParseResponseDto> parse(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(value = "taxInputId", required = false) Long taxInputId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        // 기존 분석을 참조하는 PDF 요청은 해당 분석의 접근 권한을 확인한다.
        return ApiResponse.ok(
                service.parse(principal, taxInputId, file)
        );
    }
}