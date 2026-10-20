package com.kh.back.TaxUpload.service;

import com.kh.back.TaxUpload.dto.PdfParseResponseDto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PythonPdfClient {

    private final RestClient client;

    public PythonPdfClient(
            @Value("${python.api-url:http://127.0.0.1:8000}")
            String baseUrl
    ) {
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * 실제 PDF를 Python FastAPI 서버로 전송하여 분석한다.
     */
    public PdfParseResponseDto analyze(
            byte[] pdfBytes,
            String fileName
    ) {

        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException(
                    "분석할 PDF 데이터가 없습니다."
            );
        }

        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException(
                    "PDF 파일명이 없습니다."
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
                throw new IllegalStateException(
                        "Python 서버에서 분석 결과를 반환하지 않았습니다."
                );
            }

            return response;

        } catch (RestClientException e) {

            throw new IllegalStateException(
                    "Python PDF 분석 서버 호출에 실패했습니다. "
                            + "Python 서버 실행 상태와 "
                            + "/api/pdf/analyze 경로를 확인해 주세요.",
                    e
            );
        }
    }
}