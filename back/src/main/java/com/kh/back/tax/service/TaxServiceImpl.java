package com.kh.back.tax.service;

import com.kh.back.tax.dto.TaxDto;
import com.kh.back.tax.mapper.TaxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

// 세금 분석 결과 조회와 계산을 처리한다.
@Service
@RequiredArgsConstructor
public class TaxServiceImpl implements TaxService {

    private final TaxMapper taxMapper;

    // 분석번호로 실제 총지출을 조회한다.
    @Override
    @Transactional(readOnly = true)
    public TaxDto getTotalExpense(Long taxInputId) {
        BigDecimal totalExpense = taxMapper.findTotalExpense(taxInputId);

        // 이번에 조회하지 않은 총수입은 null로 둔다.
        return new TaxDto(taxInputId, totalExpense, null);
    }

    // 분석번호로 세전 총수입을 조회한다.
    @Override
    @Transactional(readOnly = true)
    public TaxDto getTotalIncome(Long taxInputId) {
        BigDecimal totalIncome = taxMapper.findTotalIncome(taxInputId);

        // 이번에 조회하지 않은 총지출은 null로 둔다.
        return new TaxDto(taxInputId, null, totalIncome);
    }
}
