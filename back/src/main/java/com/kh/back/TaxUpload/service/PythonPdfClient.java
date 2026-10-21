package com.kh.back.TaxUpload.service;

import com.kh.back.TaxUpload.dto.PageOcrResultDto;
import com.kh.back.TaxUpload.dto.PdfParseResponseDto;

import java.util.List;

public interface PythonPdfClient {

    /**
     * PDF와 페이지별 OCR 결과를 Python 서버로 전달한다.
     */
    PdfParseResponseDto analyze(
            byte[] pdfBytes,
            String fileName,
            List<PageOcrResultDto> pages
    );
}