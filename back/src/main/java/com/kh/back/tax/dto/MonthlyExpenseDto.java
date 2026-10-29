package com.kh.back.tax.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 한 달의 지출 금액을 전달한다. 올해 자료의 남은 달에는 예상 금액을 담는다.
@Getter
@Setter
@NoArgsConstructor
public class MonthlyExpenseDto {

    private Integer month;
    private Long actualExpense;
}
