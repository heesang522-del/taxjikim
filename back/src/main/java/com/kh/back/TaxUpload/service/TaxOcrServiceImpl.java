package com.kh.back.taxupload.service;

import com.kh.back.taxupload.dto.PageOcrResultDto;
import com.kh.back.common.exception.ApiException;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TaxOcrServiceImpl implements TaxOcrService {

    @Value("${tax.ocr.tessdata-path}")
    private String tessdataPath;

    @Value("${tax.ocr.language:kor+eng}")
    private String language;

    @Value("${tax.ocr.dpi:200}")
    private int dpi;

    @Value("${tax.ocr.max-pages:50}")
    private int maxPages;

    @Value("${tax.ocr.max-rendered-pixels:12000000}")
    private long maxRenderedPixels;

    @Override
    public List<PageOcrResultDto> extractPages(
            byte[] pdfBytes
    ) throws IOException {

        if (pdfBytes == null || pdfBytes.length == 0) {
            throw invalid("PDF 데이터가 없습니다.");
        }

        if (dpi <= 0
                || maxPages <= 0
                || maxRenderedPixels <= 0) {

            throw new IllegalStateException(
                    "OCR 처리 한도 설정을 확인해주세요."
            );
        }

        List<PageOcrResultDto> results = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {

            int pageCount = document.getNumberOfPages();

            if (pageCount > maxPages) {
                throw invalid(
                        "PDF 페이지 수가 허용 범위를 초과했습니다."
                );
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            PDFRenderer renderer = new PDFRenderer(document);

            Tesseract tesseract = null;

            for (int index = 0; index < pageCount; index++) {

                int pageNumber = index + 1;

                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);

                String text = stripper.getText(document);

                boolean ocrUsed =
                        text == null || text.isBlank();

                if (ocrUsed) {

                    // 렌더링할 이미지 크기로 메모리 사용량을 제한한다.
                    PDRectangle box =
                            document.getPage(index).getCropBox();

                    double scale = dpi / 72.0;

                    long width = (long) Math.ceil(
                            box.getWidth() * scale
                    );

                    long height = (long) Math.ceil(
                            box.getHeight() * scale
                    );

                    if (width <= 0
                            || height <= 0
                            || width > maxRenderedPixels / height) {

                        throw invalid(
                                "PDF 이미지 크기가 허용 범위를 초과했습니다."
                        );
                    }

                    // 이미지 페이지가 있을 때만 OCR 엔진을 초기화한다.
                    if (tesseract == null) {
                        tesseract = new Tesseract();
                        tesseract.setDatapath(tessdataPath);
                        tesseract.setLanguage(language);
                    }

                    BufferedImage image =
                            renderer.renderImageWithDPI(
                                    index,
                                    dpi
                            );

                    try {
                        text = tesseract.doOCR(image);

                    } catch (TesseractException e) {
                        throw new ApiException(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "OCR_UNAVAILABLE",
                                pageNumber
                                        + "페이지 OCR 처리에 실패했습니다."
                        );

                    } finally {
                        image.flush();
                    }
                }

                results.add(
                        PageOcrResultDto.builder()
                                .pageNumber(pageNumber)
                                .text(text == null ? "" : text)
                                .ocrUsed(ocrUsed)
                                .build()
                );
            }
        }

        return results;
    }


    private ApiException invalid(String message) {

        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                message,
                Map.of("file", message)
        );
    }
}