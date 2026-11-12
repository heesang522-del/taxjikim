package com.kh.back.tax.dto;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// SIMULATION 테이블의 추가 가정과 저장 당시 결과 합계를 전달한다.
@Getter
@Setter
@NoArgsConstructor
public class SimulationScenarioDto {
    private Long simulationId;
    private Long taxInputId; // 이 시나리오의 기준 분석번호
    private String name;
    private BigDecimal extraInc;
    private BigDecimal extraExp;
    private BigDecimal extraDonation;
    private BigDecimal totalTax;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
}
