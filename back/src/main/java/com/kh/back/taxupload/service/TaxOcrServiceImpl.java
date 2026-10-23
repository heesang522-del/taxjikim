package com.kh.back.taxupload.service;

import com.kh.back.common.exception.ApiException;
import com.kh.back.taxupload.dto.PageOcrResultDto;
import com.kh.back.taxupload.dto.PageOcrResultDto.OcrWord;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TaxOcrServiceImpl implements TaxOcrService {

    private final String tessdataPath;
    private final String language;
    private final int dpi;
    private final int maxPages;
    private final long maxRenderedPixels;

    public TaxOcrServiceImpl(
            @Value("${tax.ocr.tessdata-path:./tessdata}")
            String tessdataPath,
            @Value("${tax.ocr.language:kor+eng}")
            String language,
            @Value("${tax.ocr.dpi:200}")
            int dpi,
            @Value("${tax.ocr.max-pages:50}")
            int maxPages,
            @Value("${tax.ocr.max-rendered-pixels:12000000}")
            long maxRenderedPixels
    ) {
        this.tessdataPath = tessdataPath;
        this.language = language;
        this.dpi = dpi;
        this.maxPages = maxPages;
        this.maxRenderedPixels = maxRenderedPixels;
    }

    @Override
    public List<PageOcrResultDto> extractPages(
            byte[] pdfBytes
    ) throws IOException {
        validateSettings();

        if (pdfBytes == null || pdfBytes.length == 0) {
            throw invalid("PDF 데이터가 없습니다.");
        }

        List<PageOcrResultDto> results = new ArrayList<>();

        try (PDDocument document = openPdf(pdfBytes)) {
            validateDocument(document);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            PDFRenderer renderer = new PDFRenderer(document);

            Tesseract tesseract = null;

            for (int index = 0;
                 index < document.getNumberOfPages();
                 index++) {
                int pageNumber = index + 1;

                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);

                String nativeText = stripper.getText(document);

                PDPage page = document.getPage(index);

                Set<COSDictionary> visited =
                        Collections.newSetFromMap(
                                new IdentityHashMap<>()
                        );

                /*
                 * 제목만 텍스트이고 거래 표는 이미지인 PDF도
                 * OCR 대상에 포함한다.
                 */
                boolean ocrUsed = nativeText == null
                        || nativeText.isBlank()
                        || hasImages(page.getResources(), visited);

                List<OcrWord> words = List.of();
                String text = nativeText == null ? "" : nativeText;

                if (ocrUsed) {
                    validateImageSize(page.getCropBox());

                    if (tesseract == null) {
                        validateLanguageData();

                        tesseract = new Tesseract();
                        tesseract.setDatapath(tessdataPath);
                        tesseract.setLanguage(language);
                    }

                    BufferedImage image =
                            renderer.renderImageWithDPI(index, dpi);

                    try {
                        List<Word> recognized = tesseract.getWords(
                                image,
                                ITessAPI.TessPageIteratorLevel.RIL_WORD
                        );

                        words = convertWords(recognized);

                        text = words.stream()
                                .map(OcrWord::getText)
                                .collect(Collectors.joining(" "));

                    } catch (RuntimeException exception) {
                        throw new ApiException(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "OCR_UNAVAILABLE",
                                pageNumber + "페이지 OCR 처리에 실패했습니다."
                        );

                    } finally {
                        image.flush();
                    }
                }

                results.add(
                        PageOcrResultDto.builder()
                                .pageNumber(pageNumber)
                                .text(text)
                                .ocrUsed(ocrUsed)
                                .words(words)
                                .build()
                );
            }
        }

        return results;
    }

    private List<OcrWord> convertWords(List<Word> recognized) {
        if (recognized == null) {
            return List.of();
        }

        double pointScale = 72.0 / dpi;

        List<OcrWord> result = new ArrayList<>();

        for (Word word : recognized) {
            String text = word.getText();

            if (text == null || text.isBlank()) {
                continue;
            }

            Rectangle box = word.getBoundingBox();

            result.add(
                    OcrWord.builder()
                            .text(text.trim())
                            .x0(box.getX() * pointScale)
                            .x1(
                                    (box.getX() + box.getWidth())
                                            * pointScale
                            )
                            .top(box.getY() * pointScale)
                            .build()
            );
        }

        return result;
    }

    private boolean hasImages(
            PDResources resources,
            Set<COSDictionary> visited
    ) throws IOException {
        if (resources == null
                || !visited.add(resources.getCOSObject())) {
            return false;
        }

        for (var name : resources.getXObjectNames()) {
            PDXObject object = resources.getXObject(name);

            if (object instanceof PDImageXObject) {
                return true;
            }

            if (object instanceof PDFormXObject form
                    && hasImages(form.getResources(), visited)) {
                return true;
            }
        }

        return false;
    }

    private void validateSettings() {
        if (dpi <= 0
                || maxPages <= 0
                || maxRenderedPixels <= 0) {
            throw new IllegalStateException(
                    "OCR 처리 한도 설정을 확인해주세요."
            );
        }
    }

    private void validateLanguageData() {
        if (tessdataPath == null
                || tessdataPath.isBlank()
                || language == null
                || language.isBlank()) {
            throw ocrDataError();
        }

        Path directory = Path.of(tessdataPath);

        if (!Files.isDirectory(directory)) {
            throw ocrDataError();
        }

        for (String item : language.split("\\+")) {
            String code = item.trim();

            if (!code.matches("[A-Za-z0-9_]+")
                    || !Files.isReadable(
                    directory.resolve(code + ".traineddata")
            )) {
                throw ocrDataError();
            }
        }
    }

    private PDDocument openPdf(byte[] pdfBytes) {
        try {
            return Loader.loadPDF(pdfBytes);
        } catch (IOException exception) {
            throw invalid(
                    "손상되었거나 열 수 없는 PDF 파일입니다."
            );
        }
    }

    private void validateDocument(PDDocument document) {
        if (document.isEncrypted()) {
            throw invalid("암호화되지 않은 PDF를 업로드해주세요.");
        }

        int pageCount = document.getNumberOfPages();

        if (pageCount == 0) {
            throw invalid("PDF에 페이지가 없습니다.");
        }

        if (pageCount > maxPages) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PDF_PAGE_LIMIT_EXCEEDED",
                    "PDF 페이지 수가 허용 범위를 초과했습니다."
            );
        }
    }

    private void validateImageSize(PDRectangle box) {
        double scale = dpi / 72.0;

        long width =
                (long) Math.ceil(box.getWidth() * scale);

        long height =
                (long) Math.ceil(box.getHeight() * scale);

        if (width <= 0
                || height <= 0
                || width > maxRenderedPixels / height) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PDF_IMAGE_LIMIT_EXCEEDED",
                    "PDF 이미지 크기가 허용 범위를 초과했습니다."
            );
        }
    }

    private ApiException ocrDataError() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "OCR_DATA_NOT_CONFIGURED",
                "OCR 언어 데이터 경로 또는 언어 파일을 확인해주세요."
        );
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