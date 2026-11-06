package com.kh.back.taxupload.service;

import com.kh.back.common.exception.ApiException;
import com.kh.back.taxupload.dto.PageOcrResultDto;
import com.kh.back.taxupload.dto.PageOcrResultDto.OcrWord;
import com.kh.back.taxupload.validator.TaxValidator;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TaxOcrServiceImpl implements TaxOcrService {

    private final TaxValidator taxValidator;
    private final String tessdataPath;
    private final String language;
    private final int dpi;
    private final int maxPages;
    private final long maxRenderedPixels;

    public TaxOcrServiceImpl(
            TaxValidator taxValidator,
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
        this.taxValidator = taxValidator;
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
        // 기존과 동일하게 설정 검사 후 입력 데이터를 검사한다.
        taxValidator.validateOcrSettings(
                dpi,
                maxPages,
                maxRenderedPixels
        );

        taxValidator.validateOcrPdfBytes(pdfBytes);

        List<PageOcrResultDto> results = new ArrayList<>();

        // 문서가 정상적으로 열렸을 때만 속성을 검사하고 추출을 시작한다.
        try (PDDocument document = openPdf(pdfBytes)) {
            taxValidator.validateOcrDocument(
                    document.isEncrypted(),
                    document.getNumberOfPages(),
                    maxPages
            );

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

                // 텍스트가 없거나 이미지가 포함된 페이지는 OCR을 수행한다.
                // 제목만 텍스트이고 거래 표가 이미지인 문서도 처리한다.
                boolean ocrUsed = nativeText == null
                        || nativeText.isBlank()
                        || hasImages(page.getResources(), visited);

                List<OcrWord> words = List.of();
                String text = nativeText == null ? "" : nativeText;

                if (ocrUsed) {
                    PDRectangle cropBox = page.getCropBox();

                    taxValidator.validateOcrImageSize(
                            cropBox.getWidth(),
                            cropBox.getHeight(),
                            dpi,
                            maxRenderedPixels
                    );

                    if (tesseract == null) {
                        // OCR이 실제 필요한 경우에만 언어 파일을 검사한다.
                        taxValidator.validateOcrLanguageData(
                                tessdataPath,
                                language
                        );

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
                        // OCR 실행 중 발생한 오류는 서비스에서 변환한다.
                        throw new ApiException(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "OCR_UNAVAILABLE",
                                pageNumber + "페이지 OCR 처리에 실패했습니다."
                        );

                    } finally {
                        // 페이지 처리 성공 여부와 관계없이 이미지 자원을 해제한다.
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

    private PDDocument openPdf(byte[] pdfBytes) {
        try {
            return Loader.loadPDF(pdfBytes);

        } catch (IOException exception) {
            // 파일 열기 실패는 검증 조건이 아닌 외부 라이브러리 오류 처리다.
            String message =
                    "손상되었거나 열 수 없는 PDF 파일입니다.";

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    message,
                    Map.of("file", message)
            );
        }
    }

    private List<OcrWord> convertWords(List<Word> recognized) {
        if (recognized == null) {
            return List.of();
        }

        // OCR 픽셀 좌표를 PDF의 포인트 단위로 변환한다.
        double pointScale = 72.0 / dpi;

        List<OcrWord> result = new ArrayList<>();

        for (Word word : recognized) {
            String text = word.getText();

            // 빈 인식 결과는 응답 단어 목록에서 제외한다.
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
                            // 단어 하단도 같은 좌표 단위로 전달한다.
                            .bottom(
                                    (box.getY() + box.getHeight())
                                            * pointScale
                            )
                            .build()
            );
        }

        return result;
    }

    private boolean hasImages(
            PDResources resources,
            Set<COSDictionary> visited
    ) throws IOException {
        // 같은 리소스를 반복 탐색하지 않도록 방문 정보를 확인한다.
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
}