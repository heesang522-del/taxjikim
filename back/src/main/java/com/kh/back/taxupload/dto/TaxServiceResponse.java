package com.kh.back.taxupload.dto;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxInputRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxServiceResponse {

    // 저장된 분석 기본정보
    private TaxInputRequest input;

    // 저장된 거래 목록
    private List<TaxTransactionDto> transactions;
}