package com.kh.back.taxupload.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.kh.back.taxupload.dto.PageOcrResultDto;
import com.kh.back.taxupload.dto.PdfParseResponseDto;
import com.kh.back.common.exception.ApiException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Slf4j
@Service
public class PythonPdfClientImpl implements PythonPdfClient {

    private final RestClient client;

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    public PythonPdfClientImpl(
            @Value("${python.api-url:http://127.0.0.1:8000}")
            String baseUrl
    ) {

        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public PdfParseResponseDto analyze(
            byte[] pdfBytes,
            String fileName,
            List<PageOcrResultDto> pages
    ) {

        if (pdfBytes == null
                || pdfBytes.length == 0
                || fileName == null
                || fileName.isBlank()) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "분석할 PDF 데이터와 파일명을 확인해주세요."
            );
        }

        ByteArrayResource resource =
                new ByteArrayResource(pdfBytes) {

                    @Override
                    public String getFilename() {
                        return fileName;
                    }
                };

        MultiValueMap<String, Object> parts =
                new LinkedMultiValueMap<>();

        parts.add("file", resource);

        try {
            // Python에서 받는 필드 이름과 동일하게 전달한다.
            parts.add(
                    "ocrPages",
                    objectMapper.writeValueAsString(
                            pages == null ? List.of() : pages
                    )
            );

        } catch (JsonProcessingException e) {

            log.error("OCR 결과 직렬화 실패", e);

            throw new IllegalStateException(
                    "OCR 결과를 직렬화할 수 없습니다.",
                    e
            );
        }

        try {
            PdfParseResponseDto response =
                    client.post()
                            .uri("/api/pdf/analyze")
                            .contentType(
                                    MediaType.MULTIPART_FORM_DATA
                            )
                            .body(parts)
                            .retrieve()
                            .body(PdfParseResponseDto.class);

            if (response == null) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "PDF_ANALYSIS_FAILED",
                        "PDF 분석 서버가 빈 응답을 반환했습니다."
                );
            }

            return response;

        } catch (RestClientResponseException e) {

            int status = e.getStatusCode().value();

            log.error(
                    "Python PDF 분석 서버 오류: 상태 코드={}",
                    status,
                    e
            );

            if (status == 400 || status == 422) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "PDF 파일 또는 OCR 입력 형식을 확인해주세요."
                );
            }

            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PDF_ANALYSIS_FAILED",
                    "PDF 분석 서버 처리에 실패했습니다."
            );

        } catch (ResourceAccessException e) {

            // 실제 연결 실패와 통신 시간 초과를 처리한다.
            log.error("Python PDF 분석 서버 통신 실패", e);

            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PDF_ANALYSIS_UNAVAILABLE",
                    "PDF 분석 서버와 통신할 수 없습니다."
            );

        } catch (RestClientException e) {

            // 응답을 DTO로 변환하지 못한 경우 등도 기록한다.
            log.error("Python PDF 분석 요청 또는 응답 처리 실패", e);

            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PDF_RESPONSE_ERROR",
                    "PDF 분석 응답을 처리할 수 없습니다."
            );
        }
    }
}