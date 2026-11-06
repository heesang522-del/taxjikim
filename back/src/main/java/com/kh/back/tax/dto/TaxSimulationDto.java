package com.kh.back.tax.dto;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 기존 결과의 계산 조건과 추가 가정을 전달한다. DB 저장용 데이터가 아니다.
@Getter
@Setter
@NoArgsConstructor
public class TaxSimulationDto {
    private TaxCalculationInputDto input; // 기존 합계·가족 수·기부금
    private BigDecimal extraInc = BigDecimal.ZERO; // 추가 세전 수입
    private BigDecimal extraExp = BigDecimal.ZERO; // 추가 사업 지출
    private BigDecimal extraDonation = BigDecimal.ZERO; // 일반기부금에 합칠 추가 기부금
}
