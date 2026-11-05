package com.kh.back.tax.dto;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// DB에서 받은 카테고리 합계에 비율을 채워 결과 화면으로 전달한다.
@Getter
@Setter
@NoArgsConstructor
public class CategoryAmountDto {
    private String transactionType; // INCOME / EXPENSE
    private String category;        // 카테고리 코드
    private BigDecimal amount;      // 실제 거래의 카테고리 합계
    private BigDecimal percentage;  // DB 조회 후 서비스에서 계산하는 비율
}
