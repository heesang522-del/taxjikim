package com.kh.back.tax.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// DB에서 월·거래 구분별로 함께 조회한 합계를 담는다.
@Getter
@Setter
@NoArgsConstructor
public class MonthlyTransactionDto {
    private Integer month;
    private String transactionType;
    private Long amount;
    private BigDecimal netIncome; // 수입의 실제 세후 입금액 합계, 지출 또는 누락 시 null
}
