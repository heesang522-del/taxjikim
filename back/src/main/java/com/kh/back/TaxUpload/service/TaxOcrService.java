package com.kh.back.taxupload.service;

import com.kh.back.taxupload.dto.PageOcrResultDto;

import java.io.IOException;
import java.util.List;

public interface TaxOcrService {

    /**
     * PDF의 페이지별 텍스트를 추출한다.
     * 텍스트가 없는 페이지는 OCR로 처리한다.
     */
    List<PageOcrResultDto> extractPages(
            byte[] pdfBytes
    ) throws IOException;

}