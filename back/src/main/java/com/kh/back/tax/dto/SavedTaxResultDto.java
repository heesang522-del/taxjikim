package com.kh.back.tax.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 마이페이지 목록과 연도별 비교에 필요한 저장 결과 요약을 전달한다.
@Getter
@Setter
public class SavedTaxResultDto {
    private Long taxInputId;
    private Integer taxYear;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal totalTax;
    // DB의 한국 시간 저장 시각을 시간대가 있는 문자열로 전달한다.
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'+09:00'")
    private LocalDateTime savedAt;
}
