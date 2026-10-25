package com.kh.back.taxupload.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.kh.back.common.exception.ApiException;
import com.kh.back.taxupload.dto.PageOcrResultDto;
import com.kh.back.taxupload.dto.PdfParseResponseDto;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.util.List;

@Slf4j
@Service
public class PythonPdfClientImpl implements PythonPdfClient {

    private final RestClient client;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public PythonPdfClientImpl(
            @Value("${python.api-url:http://127.0.0.1:8000}")
            String baseUrl,
            @Value("${python.connect-timeout-ms:5000}")
            int connectTimeout,
            @Value("${python.read-timeout-ms:90000}")
            int readTimeout
    ) {
        if (connectTimeout <= 0 || readTimeout <= 0) {
            throw new IllegalArgumentException(
                    "Python 통신 제한 시간은 양수여야 합니다."
            );
        }

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
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
                    "분석할 PDF 데이터와 파일명이 필요합니다."
            );
        }

        MultiValueMap<String, Object> parts =
                createParts(pdfBytes, fileName, pages);

        try {
            PdfParseResponseDto response = client.post()
                    .uri("/api/pdf/analyze")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(PdfParseResponseDto.class);

            if (response == null) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "PDF_EMPTY_RESPONSE",
                        "PDF 분석 서버가 빈 응답을 반환했습니다."
                );
            }

            return response;

        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();

            // 금융 거래 원문이 들어갈 수 있는 응답 본문은 기록하지 않는다.
            log.warn("Python PDF 서버 응답 오류: status={}", status);

            if (status == 400 || status == 422) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "PDF_ANALYSIS_INPUT_INVALID",
                        "PDF 파일 또는 OCR 입력 형식을 확인해주세요."
                );
            }

            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PDF_ANALYSIS_FAILED",
                    "PDF 분석 서버 처리에 실패했습니다."
            );

        } catch (ResourceAccessException exception) {
            if (hasTimeoutCause(exception)) {
                throw new ApiException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "PDF_ANALYSIS_TIMEOUT",
                        "PDF 분석 서버의 응답 시간이 초과되었습니다."
                );
            }

            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PDF_ANALYSIS_UNAVAILABLE",
                    "PDF 분석 서버와 통신할 수 없습니다."
            );

        } catch (RestClientException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PDF_RESPONSE_ERROR",
                    "PDF 분석 응답을 처리할 수 없습니다."
            );
        }
    }

    private MultiValueMap<String, Object> createParts(
            byte[] pdfBytes,
            String fileName,
            List<PageOcrResultDto> pages
    ) {
        ByteArrayResource resource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };

        MultiValueMap<String, Object> parts =
                new LinkedMultiValueMap<>();

        parts.add("file", resource);

        List<PageOcrResultDto> ocrPages = pages == null
                ? List.of()
                : pages.stream()
                .filter(PageOcrResultDto::isOcrUsed)
                .toList();

        try {
            parts.add(
                    "ocrPages",
                    objectMapper.writeValueAsString(ocrPages)
            );

        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "OCR_SERIALIZATION_FAILED",
                    "OCR 결과를 전송할 수 없습니다."
            );
        }

        return parts;
    }

    private boolean hasTimeoutCause(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}