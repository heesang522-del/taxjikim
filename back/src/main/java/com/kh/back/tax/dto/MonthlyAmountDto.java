package com.kh.back.tax.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 수입·지출 API에서 공통으로 사용하는 월별 실제·예상 금액을 전달한다.
@Getter
@Setter
@NoArgsConstructor
public class MonthlyAmountDto {

    private Integer month;
    private Long amount;
    // 실제 금액은 기본값 false, 평균으로 만든 예상 금액은 true로 표시한다.
    private boolean predicted;
}
