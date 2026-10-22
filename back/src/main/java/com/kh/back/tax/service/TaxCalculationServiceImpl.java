package com.kh.back.tax.service;

import com.kh.back.tax.dto.TaxBaseResultDto;
import com.kh.back.tax.dto.TaxCalculationInputDto;
import org.springframework.stereotype.Service;

@Service
public class TaxCalculationServiceImpl implements TaxCalculationService {

    @Override
    public TaxBaseResultDto calculateTaxBase(TaxCalculationInputDto input) {
        // 연간 금액 합산과 기부금 한도 검토가 끝난 입력을 사용한다.
        // 세율 적용·창업감면·최종세액 계산은 이후 단계에서 처리한다.
        if (input == null) {
            throw new IllegalArgumentException("계산 입력이 필요합니다.");
        }

        // 기부금은 업무 경비와 분리해 중복 차감하지 않는다.
        long beforeDonation = input.annualGrossIncome() - input.annualBusinessExpense();
        // 적자·공제 초과 시 과세표준은 0으로 처리한다. 결손금 이월 계산은 별도다.
        long afterDonation = Math.max(0, Math.max(0, beforeDonation) - input.recognizedDonation());
        long taxableIncome = Math.max(0, afterDonation - input.incomeDeduction());

        return new TaxBaseResultDto(beforeDonation, afterDonation, taxableIncome);
    }
}
