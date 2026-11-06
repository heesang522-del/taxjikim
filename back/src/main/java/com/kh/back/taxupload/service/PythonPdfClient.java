package com.kh.back.taxupload.service;

import com.kh.back.taxupload.dto.PageOcrResultDto;
import com.kh.back.taxupload.dto.PdfParseResponseDto;

import com.kh.back.taxupload.dto.TaxCategoryChange;

import java.util.List;

public interface PythonPdfClient {

    // 확정된 거래 카테고리 목록을 Python에 전달한다.
    void sendTrainingFeedback(List<TaxCategoryChange.Feedback> feedback);

    /**
     * PDF와 페이지별 OCR 결과를 Python 서버로 전달한다.
     */
    PdfParseResponseDto analyze(
            byte[] pdfBytes,
            String fileName,
            List<PageOcrResultDto> pages
    );
}