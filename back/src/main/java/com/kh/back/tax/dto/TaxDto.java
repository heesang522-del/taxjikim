package com.kh.back.tax.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

// 세금 분석 결과 데이터를 전달한다.
@Getter
@AllArgsConstructor
public class TaxDto {

    private final Long taxInputId;
    private final BigDecimal totalExpense;
    private final BigDecimal totalIncome;
}
