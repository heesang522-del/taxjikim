package com.kh.back.TaxUpload.dto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class TaxTransactionDto {

    private Long transactionId;
    private Long taxInputId;
    private Long userId;
    private String transactionType;
    private LocalDate transactionDate;
    private String description;
    private String category;
    private BigDecimal amount;
    private Integer taxYear;
    private boolean isEdited;
    private Long sourceFileId;
    private Integer sourceItemIndex;

    public record ReviewedTransactionRequest(Long fileId, Integer itemIndex, Integer taxYear,
            String transactionType, LocalDate transactionDate, String category, String description, BigDecimal amount) {}


    // PDF 파싱 데이터를 매핑하기 위한 생성자 편의 메서드
    public TaxTransactionDto(Long taxInputId, Long userId, String category, long amount, int taxYear) {
        this.taxInputId = taxInputId;
        this.userId = userId;
        this.transactionType = "DEDUCTION";
        this.transactionDate = LocalDate.of(taxYear, 12, 31);
        this.description = category;
        this.category = category;
        this.amount = BigDecimal.valueOf(amount);
        this.taxYear = taxYear;
        this.isEdited = false;
    }
}
