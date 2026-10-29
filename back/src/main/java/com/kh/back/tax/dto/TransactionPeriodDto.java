package com.kh.back.tax.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

// 분석에 저장된 전체 거래의 첫 날짜와 마지막 날짜를 전달한다.
@Getter
@Setter
@NoArgsConstructor
public class TransactionPeriodDto {

    private LocalDate firstDate;
    private LocalDate lastDate;
}
