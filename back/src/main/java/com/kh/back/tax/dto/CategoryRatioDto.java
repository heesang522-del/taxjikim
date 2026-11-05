package com.kh.back.tax.dto;

import java.math.BigDecimal;

public record CategoryRatioDto(String category, BigDecimal amount, BigDecimal percentage) {
}
