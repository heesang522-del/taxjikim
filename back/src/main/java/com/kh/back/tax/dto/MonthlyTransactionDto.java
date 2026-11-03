package com.kh.back.tax.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// DB에서 월·거래 구분별로 함께 조회한 합계를 담는다.
@Getter
@Setter
@NoArgsConstructor
public class MonthlyTransactionDto {
    private Integer month;
    private String transactionType;
    private Long amount;
}
