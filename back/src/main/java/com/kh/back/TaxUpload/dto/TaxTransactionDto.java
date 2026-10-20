package com.kh.back.TaxUpload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    private String transactionType;

    private String status;

    private Integer pageNumber;

    private String rawText;
}
