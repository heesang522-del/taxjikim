package com.kh.back.TaxUpload.dto;

import java.util.List;
import java.math.BigDecimal;

public record TaxPdfUploadResult(Long fileId, String originalFileName,
                                 List<TaxTransactionDto> transactions, int skippedCount,
                                 boolean persisted, TaxPdfDocumentKind documentKind,
                                 YearEndEvidenceResult evidence) {
    // 기존 장부 API 응답 필드는 유지해 기존 프론트 호출과 호환한다.
    public TaxPdfUploadResult(Long fileId, String originalFileName,
            List<TaxTransactionDto> transactions, int skippedCount, boolean persisted) {
        this(fileId, originalFileName, transactions, skippedCount, persisted, TaxPdfDocumentKind.LEDGER, null);
    }

    public enum TaxPdfDocumentKind { LEDGER, YEAR_END_EVIDENCE }

public record YearEndEvidenceResult(EvidenceType evidenceType, Integer documentYear,
        int requestedTaxYear, ReviewStatus status, List<Item> items, List<String> notices) {
    public enum EvidenceType { CREDIT_CARD, DEBIT_CARD, MEDICAL, UNKNOWN }
    public enum ReviewStatus { NEEDS_REVIEW, UNSUPPORTED_FORMAT, YEAR_MISMATCH }
    public record Item(String label, BigDecimal reportedAmount, int page, boolean confirmed,
                       String transactionDate, String valueKind, String transactionMonth, String transactionType) {
        public Item(String label, BigDecimal reportedAmount, int page, boolean confirmed, String transactionDate, String valueKind, String transactionMonth) {
            this(label, reportedAmount, page, confirmed, transactionDate, valueKind, transactionMonth, null);
        }
        public Item(String label, BigDecimal reportedAmount, int page, boolean confirmed, String transactionDate, String valueKind) {
            this(label, reportedAmount, page, confirmed, transactionDate, valueKind, null);
        }
        public Item(String label, BigDecimal reportedAmount, int page, boolean confirmed) {
            this(label, reportedAmount, page, confirmed, null, "SUMMARY");
        }
    }
}

}
