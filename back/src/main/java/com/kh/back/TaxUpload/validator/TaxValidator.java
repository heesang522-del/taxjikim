package com.kh.back.TaxUpload.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Service
public class TaxValidator {

    private static final int MAX_BYTES = 10 * 1024 * 1024;

    @Value("${tax.ocr.max-pages:50}")
    private int maxPages;

    public void validatePdf(byte[] bytes) throws IOException {

        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("파일이 비어 있습니다.");
        }

        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("PDF는 10MB 이하여야 합니다.");
        }

        if (bytes.length < 5 ||
                !Arrays.equals(
                        Arrays.copyOfRange(bytes, 0, 5),
                        "%PDF-".getBytes(StandardCharsets.US_ASCII)
                )) {
            throw new IllegalArgumentException("PDF 형식이 아닙니다.");
        }

        try (PDDocument document = Loader.loadPDF(bytes)) {

            if (document.isEncrypted()) {
                throw new IllegalArgumentException(
                        "암호화된 PDF는 지원하지 않습니다."
                );
            }

            int count = document.getNumberOfPages();

            if (count == 0 || count > maxPages) {
                throw new IllegalArgumentException(
                        "PDF 페이지 수가 허용 범위를 벗어났습니다."
                );
            }
        }
    }
}