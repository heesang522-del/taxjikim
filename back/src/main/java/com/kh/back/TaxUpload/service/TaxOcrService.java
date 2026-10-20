
package com.kh.back.TaxUpload.service;

import com.kh.back.TaxUpload.dto.PageOcrResultDto;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaxOcrService {

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

    /**
     * PDF 페이지별 텍스트 추출
     *
     * 텍스트형 PDF: PDFBox
     * 이미지형 PDF: Tess4J
     *
     * 실제 PDF 데이터만 사용하며,
     * 거래 금액이나 거래 유형을 생성하지 않는다.
     */
    public List<PageOcrResultDto> extractPages(
            byte[] pdfBytes
    ) throws IOException {

        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException(
                    "PDF 데이터가 없습니다."
            );
        }

        List<PageOcrResultDto> results =
                new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {

            int pageCount = document.getNumberOfPages();

            if (pageCount > maxPages) {
                throw new IllegalArgumentException(
                        "PDF 페이지 수가 허용 범위를 초과했습니다."
                );
            }

            PDFRenderer renderer =
                    new PDFRenderer(document);

            PDFTextStripper stripper =
                    new PDFTextStripper();

            Tesseract tesseract = new Tesseract();

            tesseract.setDatapath(tessdataPath);
            tesseract.setLanguage(language);

            for (int pageIndex = 0;
                 pageIndex < pageCount;
                 pageIndex++) {

                int pageNumber = pageIndex + 1;

                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);

                String extractedText =
                        stripper.getText(document);

                if (extractedText != null
                        && !extractedText.isBlank()) {

                    results.add(
                            PageOcrResultDto.builder()
                                    .pageNumber(pageNumber)
                                    .text(extractedText)
                                    .ocrUsed(false)
                                    .build()
                    );

                    continue;
                }

                PDRectangle mediaBox =
                        document.getPage(pageIndex)
                                .getMediaBox();

                double scale = dpi / 72.0;

                long width = (long) Math.ceil(
                        mediaBox.getWidth() * scale
                );

                long height = (long) Math.ceil(
                        mediaBox.getHeight() * scale
                );

                if (width <= 0 || height <= 0
                        || width > maxRenderedPixels / height) {

                    throw new IllegalArgumentException(
                            "PDF 이미지 크기가 허용 범위를 초과했습니다."
                    );
                }

                BufferedImage image =
                        renderer.renderImageWithDPI(
                                pageIndex,
                                dpi
                        );

                try {
                    String ocrText =
                            tesseract.doOCR(image);

                    results.add(
                            PageOcrResultDto.builder()
                                    .pageNumber(pageNumber)
                                    .text(
                                            ocrText == null
                                                    ? ""
                                                    : ocrText
                                    )
                                    .ocrUsed(true)
                                    .build()
                    );

                } catch (TesseractException e) {

                    throw new IOException(
                            pageNumber
                                    + "페이지 OCR 처리에 실패했습니다.",
                            e
                    );

                } finally {
                    image.flush();
                }
            }
        }

        return results;
    }

    /**
     * PDF 전체 텍스트 추출
     */
    public String extractText(
            byte[] pdfBytes
    ) throws IOException {

        List<PageOcrResultDto> pages =
                extractPages(pdfBytes);

        StringBuilder result =
                new StringBuilder();

        for (PageOcrResultDto page : pages) {

            if (result.length() > 0) {
                result.append(System.lineSeparator());
            }

            result.append(page.getText());
        }

        return result.toString();
    }
}
