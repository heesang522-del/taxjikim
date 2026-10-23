package com.kh.back.taxupload.dto;

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

    private BigDecimal actualAmount;  // 실제금액

    private Integer taxYear;   // 귀속연도

    private Boolean edited;    // 수정됬는지 여부

    private String status;     // 상태

    private Integer pageNumber;  // 페이지 숫자

    private String rawText;     // 원본텍스트

    private Boolean parseSuccess; // 파싱성공 여부

    private Boolean excluded;  // 제외

    private String failureReason; // 실패 이유
}