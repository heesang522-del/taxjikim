package com.kh.back.TaxUpload.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxTransactionDto {

    private Long transactionId;
    private Long userId;

    private LocalDate transactionDate;
    private BigDecimal amount;
    private String description;

    // INCOME / EXPENSE / UNKNOWN
    private String transactionType;

    // REVIEW_REQUIRED / CONFIRMED
    private String status;

    private Integer pageNumber;
    private String rawText;
}