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

    private Long taxInputId;

    private Long userId;

    private LocalDate transactionDate;

    private String transactionType;

    private String description;

    private String category;

    private BigDecimal amount;

    private BigDecimal preTaxIncome;

    private BigDecimal taxDeducted;

    private BigDecimal actualAmount;

    private Integer taxYear;

    private Boolean edited;

    private String status;

    private Integer pageNumber;

    private String rawText;

    private Boolean parseSuccess;

    private Boolean excluded;

    private String failureReason;
}