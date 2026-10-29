package com.kh.back.tax.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

// 분석번호와 조회한 총수입 또는 총지출 금액을 전달한다.
@Getter
@AllArgsConstructor
public class TaxDto {

    private final Long taxInputId;
    private final BigDecimal totalAmount;
}
