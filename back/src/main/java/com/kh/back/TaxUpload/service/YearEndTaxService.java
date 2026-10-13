package com.kh.back.TaxUpload.service;

import com.kh.back.TaxUpload.dto.TaxTransactionDto;
import com.kh.back.TaxUpload.mapper.TaxMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class YearEndTaxService {

    private final TaxMapper taxMapper;

    public YearEndTaxService(TaxMapper taxMapper) {
        this.taxMapper = taxMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void processTaxPdf(InputStream pdfInputStream, Long userId, Long taxInputId, int taxYear) {
        try {
            // 🛡️ 필수 파라미터 유효성 검증 (프론트엔드/세션으로부터 정상적인 ID가 넘어왔는지 확인)
            if (userId == null || userId <= 0) {
                throw new IllegalArgumentException("유효하지 않은 사용자 정보입니다. 다시 로그인해 주세요.");
            }
            if (taxInputId == null || taxInputId <= 0) {
                throw new IllegalArgumentException("세무 입력 정보가 존재하지 않습니다. 이전 단계를 먼저 완료해 주세요.");
            }

            byte[] bytes = pdfInputStream.readAllBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {

                if (document.isEncrypted()) {
                    throw new IllegalArgumentException("암호화된 PDF 파일입니다. 비밀번호 해제 후 업로드해주세요.");
                }

                // 1. PDF 텍스트 추출
                PDFTextStripper stripper = new PDFTextStripper();
                String parsedText = stripper.getText(document);

                if (parsedText == null || parsedText.trim().length() < 10) {
                    parsedText = extractTextUsingOcr(document);
                }

                // 2. 파싱 및 유효성 검증 (하드코딩 Fallback 완전 제거)
                List<TaxTransactionDto> extractedItems = parseTextToTransactions(parsedText, userId, taxInputId, taxYear);
                validateExtractedData(extractedItems);

                // 3. DB 대량 적재 (실제 존재하는 FOREIGN KEY 바인딩)
                taxMapper.insertTaxTransactions(extractedItems);
            }
        } catch (IOException e) {
            throw new RuntimeException("PDF 및 OCR 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    private String extractTextUsingOcr(PDDocument document) throws IOException {
        PDFRenderer pdfRenderer = new PDFRenderer(document);
        StringBuilder ocrResultText = new StringBuilder();

        for (int page = 0; page < document.getNumberOfPages(); ++page) {
            BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 150);
        }

        return ocrResultText.toString();
    }

    private List<TaxTransactionDto> parseTextToTransactions(String text, Long userId, Long taxInputId, int taxYear) {
        List<TaxTransactionDto> list = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");

        Pattern linePattern = Pattern.compile("(.+?)\\s+([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:원)?$");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            Matcher matcher = linePattern.matcher(line);
            if (matcher.find()) {
                String category = matcher.group(1).trim();
                String amountStr = matcher.group(2).replace(",", "");

                try {
                    long amount = Long.parseLong(amountStr);
                    if (amount > 0 && category.length() > 0) {
                        TaxTransactionDto dto = new TaxTransactionDto();
                        dto.setTaxInputId(taxInputId);
                        dto.setUserId(userId);
                        dto.setTransactionType("DEDUCTION"); // 스키마 규격에 맞는 타입
                        dto.setTransactionDate(LocalDate.of(taxYear, 12, 31));
                        dto.setDescription(category);
                        dto.setCategory(category);
                        dto.setAmount(BigDecimal.valueOf(amount));
                        dto.setTaxYear(taxYear);
                        dto.setEdited(false);

                        list.add(dto);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        return list;
    }

    private void validateExtractedData(List<TaxTransactionDto> items) {
        if (items.isEmpty()) {
            throw new IllegalStateException("PDF 문서 내에서 유효한 거래 내역(항목 및 금액)을 추출할 수 없습니다. 문서 양식을 확인해주세요.");
        }
        for (TaxTransactionDto item : items) {
            if (item.getAmount() != null && item.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("비정상적인 마이너스 금액이 감지되었습니다: " + item.getCategory());
            }
        }
    }
}