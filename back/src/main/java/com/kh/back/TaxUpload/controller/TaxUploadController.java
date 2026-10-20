
package com.kh.back.TaxUpload.controller;

import com.kh.back.TaxUpload.dto.PdfParseResponseDto;
import com.kh.back.TaxUpload.dto.TaxTransactionDto;
import com.kh.back.TaxUpload.service.YearEndTaxService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/tax")
@RequiredArgsConstructor
public class TaxUploadController {

    private final YearEndTaxService service;

    /**
     * PDF 업로드 및 자동 파싱
     *
     * 비회원도 이용 가능하도록
     * 컨트롤러에서 로그인 검사를 하지 않는다.
     *
     * 실제 접근 허용 여부는
     * Spring Security 설정에서도 결정된다.
     */
    @PostMapping(
            value = "/parse",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public PdfParseResponseDto parse(
            @RequestParam("file") MultipartFile file
    ) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "업로드할 PDF 파일이 없습니다."
            );
        }

        String originalFilename =
                file.getOriginalFilename();

        if (
                originalFilename == null ||
                        !originalFilename
                                .toLowerCase(java.util.Locale.ROOT)
                                .endsWith(".pdf")
        ) {
            throw new IllegalArgumentException(
                    "PDF 파일만 업로드할 수 있습니다."
            );
        }

        /*
         * 실제 PDF 분석 서비스 호출
         *
         * 테스트 데이터나 임의 거래를
         * 생성하지 않는다.
         */
        return service.parsePdf(file);
    }

    /**
     * 로그인 사용자 거래 저장
     *
     * 비회원 저장 불가
     */
    @PostMapping("/transactions")
    public ResponseEntity<Void> save(
            Authentication authentication,
            @RequestBody List<TaxTransactionDto> rows
    ) {

        Long userId =
                getLoginUserId(authentication);

        service.saveConfirmedTransactions(
                userId,
                rows
        );

        return ResponseEntity
                .noContent()
                .build();
    }

    /**
     * 로그인 사용자 거래 조회
     *
     * 본인 거래만 조회
     */
    @GetMapping("/transactions")
    public List<TaxTransactionDto> get(
            Authentication authentication
    ) {

        Long userId =
                getLoginUserId(authentication);

        return service.getTransactions(
                userId
        );
    }

    /**
     * 실제 로그인 여부 확인
     */
    private void requireAuthentication(
            Authentication authentication
    ) {

        if (
                authentication == null ||
                        !authentication.isAuthenticated() ||
                        authentication instanceof
                                org.springframework.security.authentication
                                        .AnonymousAuthenticationToken ||
                        "anonymousUser".equals(
                                authentication.getPrincipal()
                        )
        ) {
            throw new InsufficientAuthenticationException(
                    "로그인이 필요합니다."
            );
        }
    }

    /**
     * 실제 인증 정보에서 사용자 ID 추출
     *
     * 주의:
     * 이 구현은 authentication.getName()이
     * DB의 숫자형 user_id일 때만 동작한다.
     *
     * 현재 인증 principal이 이메일이나
     * 로그인 아이디를 반환한다면
     * 실제 인증 구조에 맞게 수정해야 한다.
     */
    private Long getLoginUserId(
            Authentication authentication
    ) {

        requireAuthentication(authentication);

        try {
            return Long.valueOf(
                    authentication.getName()
            );
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "로그인 인증 정보에서 사용자 ID를 확인할 수 없습니다.",
                    e
            );
        }
    }
}
