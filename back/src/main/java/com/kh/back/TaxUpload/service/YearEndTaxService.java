package com.kh.back.TaxUpload.service;

import com.kh.back.TaxUpload.dto.PdfParseResponseDto;
import com.kh.back.TaxUpload.dto.TaxTransactionDto;
import com.kh.back.TaxUpload.mapper.TaxMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class YearEndTaxService {

    private final TaxValidator taxValidator;
    private final TaxOcrService taxOcrService;
    private final TaxMapper taxMapper;

    private static final long MAX_BYTES =
            10L * 1024 * 1024;

    private static final Pattern DATE_PATTERN =
            Pattern.compile(
                    "(?<!\\d)(\\d{4})\\s*" +
                            "(?:[-./]|년)\\s*" +
                            "(\\d{1,2})\\s*" +
                            "(?:[-./]|월)\\s*" +
                            "(\\d{1,2})\\s*일?(?!\\d)"
            );

    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile(
                    "(?<![\\d.,])" +
                            "(\\d{1,3}(?:,\\d{3})+|\\d+\\s*원)" +
                            "(?![\\d.,])"
            );

    public PdfParseResponseDto parsePdf(
            MultipartFile file
    ) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "PDF 파일이 비어 있습니다."
            );
        }

        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException(
                    "파일 크기는 10MB 이하여야 합니다."
            );
        }

        byte[] bytes = file.getBytes();

        // 1. PDF 검증
        taxValidator.validatePdf(bytes);

        // 2. 텍스트 + OCR 추출
        TaxOcrService.OcrResult result =
                taxOcrService.extract(bytes);

        List<TaxTransactionDto> transactions =
                new ArrayList<>();

        List<String> reviewLines =
                new ArrayList<>();

        // 3. 거래 후보 파싱
        for (TaxOcrService.OcrRow row : result.rows()) {

            TaxTransactionDto transaction =
                    parseTransaction(row);

            if (transaction != null) {
                transactions.add(transaction);
            } else if (!row.text().isBlank()) {
                reviewLines.add(
                        "[페이지 " + row.page() + "] "
                                + row.text()
                );
            }
        }

        // 4. JSON 응답
        return PdfParseResponseDto.builder()
                .fileName(file.getOriginalFilename())
                .totalPages(result.totalPages())
                .extractedCount(transactions.size())
                .transactions(transactions)
                .reviewLines(reviewLines)
                .build();
    }

    private TaxTransactionDto parseTransaction(
            TaxOcrService.OcrRow row
    ) {

        String rawText = row.text();

        // PDFBox 글자 단위 추출의 불필요한 공백 제거
        String text = normalizeRow(row);

        Matcher dateMatcher =
                DATE_PATTERN.matcher(text);

        if (!dateMatcher.find()) {
            return null;
        }

        LocalDate date;

        try {
            date = LocalDate.of(
                    Integer.parseInt(dateMatcher.group(1)),
                    Integer.parseInt(dateMatcher.group(2)),
                    Integer.parseInt(dateMatcher.group(3))
            );
        } catch (DateTimeException e) {
            return null;
        }

        String remainder =
                text.substring(0, dateMatcher.start())
                        + " "
                        + text.substring(dateMatcher.end());

        Matcher amountMatcher =
                AMOUNT_PATTERN.matcher(remainder);

        List<AmountMatch> amounts = new ArrayList<>();

        while (amountMatcher.find()) {
            amounts.add(new AmountMatch(
                    amountMatcher.start(),
                    amountMatcher.end(),
                    amountMatcher.group(1)
            ));
        }

        // 금액이 없거나 복수면 임의 추정하지 않음
        if (amounts.size() != 1) {
            return null;
        }

        AmountMatch match = amounts.get(0);

        BigDecimal amount;

        try {
            String normalized = match.value()
                    .replace(",", "")
                    .replace("원", "")
                    .replaceAll("\\s+", "");

            amount = new BigDecimal(normalized);

        } catch (NumberFormatException e) {
            return null;
        }

        if (amount.signum() <= 0) {
            return null;
        }

        String description = (
                remainder.substring(0, match.start())
                        + " "
                        + remainder.substring(match.end())
        ).trim().replaceAll("\\s+", " ");

        if (description.isBlank()) {
            return null;
        }

        return TaxTransactionDto.builder()
                .transactionDate(date)
                .amount(amount)
                .description(description)
                .transactionType("UNKNOWN")
                .status("REVIEW_REQUIRED")
                .pageNumber(row.page())
                .rawText(rawText)
                .build();
    }

    private String normalizeRow(TaxOcrService.OcrRow row) {

        List<TaxOcrService.OcrWord> words = row.words();

        if (words.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        TaxOcrService.OcrWord previous = null;

        for (TaxOcrService.OcrWord word : words) {

            if (previous != null) {

                float previousRight =
                        previous.x() + previous.width();

                float gap = word.x() - previousRight;

                // 충분한 간격이 있으면 구분 공백 삽입
                float threshold = Math.max(
                        2f,
                        Math.min(
                                previous.height(),
                                word.height()
                        ) * 0.35f
                );

                if (gap > threshold) {
                    result.append(' ');
                }
            }

            result.append(word.text());
            previous = word;
        }

        return result.toString().trim();
    }

    private record AmountMatch(
            int start,
            int end,
            String value
    ) {}

    @Transactional
    public void saveConfirmedTransactions(
            Long userId,
            List<TaxTransactionDto> transactions
    ) {

        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "로그인 사용자 정보가 필요합니다."
            );
        }

        if (transactions == null || transactions.isEmpty()) {
            throw new IllegalArgumentException(
                    "저장할 거래가 없습니다."
            );
        }

        for (TaxTransactionDto transaction : transactions) {

            validateTransaction(transaction);

            // 요청 데이터의 userId, transactionId는 신뢰하지 않음
            transaction.setUserId(userId);
            transaction.setTransactionId(null);
            transaction.setStatus("CONFIRMED");

            taxMapper.insertTransaction(transaction);
        }
    }

    private void validateTransaction(
            TaxTransactionDto transaction
    ) {

        if (transaction == null ||
                transaction.getTransactionDate() == null ||
                transaction.getAmount() == null ||
                transaction.getAmount().signum() <= 0 ||
                transaction.getDescription() == null ||
                transaction.getDescription().isBlank()) {

            throw new IllegalArgumentException(
                    "거래 필수 정보가 누락되었습니다."
            );
        }

        String type = transaction.getTransactionType();

        if (!"INCOME".equals(type) &&
                !"EXPENSE".equals(type)) {

            throw new IllegalArgumentException(
                    "입금 또는 출금 유형을 확인해야 합니다."
            );
        }
    }

    @Transactional(readOnly = true)
    public List<TaxTransactionDto> getTransactions(
            Long userId
    ) {
        return taxMapper.findByUserId(userId);
    }
}