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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            boolean isGuest = (userId == null || userId <= 0);
            Long targetUserId = isGuest ? null : userId;

            // taxInputId가 없을 경우 마스터 정보 자동 조회 또는 Map 기반 생성
            if (taxInputId == null || taxInputId <= 0) {
                if (!isGuest) {
                    Long existingInputId = taxMapper.findTaxInputIdByUserAndYear(targetUserId, taxYear);
                    if (existingInputId != null && existingInputId > 0) {
                        taxInputId = existingInputId;
                    } else {
                        Map<String, Object> params = new HashMap<>();
                        params.put("userId", targetUserId);
                        params.put("taxYear", taxYear);
                        params.put("region", "미지정");
                        params.put("job", "일반 프리랜서");
                        params.put("incomeType", "FREELANCER");
                        params.put("autoWithholding", true);
                        params.put("totalIncome", BigDecimal.ZERO);
                        params.put("totalExpense", BigDecimal.ZERO);
                        params.put("expectedTax", BigDecimal.ZERO);

                        taxMapper.insertTaxInput(params);
                        taxInputId = ((Number) params.get("taxInputId")).longValue();
                    }
                } else {
                    // 비회원 체험용 임시 ID 할당
                    taxInputId = 999999L;
                }
            }

            byte[] bytes = pdfInputStream.readAllBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {
                if (document.isEncrypted()) {
                    throw new IllegalArgumentException("암호화된 PDF 파일입니다. 비밀번호 해제 후 업로드해주세요.");
                }

                PDFTextStripper stripper = new PDFTextStripper();
                String parsedText = stripper.getText(document);

                if (parsedText == null || parsedText.trim().length() < 10) {
                    parsedText = extractTextUsingOcr(document);
                }

                List<TaxTransactionDto> extractedItems = parseTextToTransactions(parsedText, targetUserId, taxInputId, taxYear);
                validateExtractedData(extractedItems);

                // 비회원이 아닐 때만 실제 DB 적재 수행
                if (!isGuest) {
                    taxMapper.insertTaxTransactions(extractedItems);
                }
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
                        dto.setTransactionType("DEDUCTION");
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
            throw new IllegalStateException("PDF 문서 내에서 유효한 거래 내역(항목 및 금액)을 추출할 수 없습니다.");
        }
    }
}