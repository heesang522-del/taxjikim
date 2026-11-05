package com.kh.back.tax.dto;

import java.math.BigDecimal;

public record CategoryAmountDto(String transactionType, String category, BigDecimal amount) {
}
