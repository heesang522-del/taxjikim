package com.kh.back.tax.service;

import com.kh.back.tax.dto.TaxDto;

// 세금 분석 결과 처리 기능을 선언한다.
public interface TaxService {

    // 분석번호에 해당하는 실제 총지출을 반환한다.
    TaxDto getTotalExpense(Long taxInputId);

    // 분석번호에 해당하는 세전 총수입을 반환한다.
    TaxDto getTotalIncome(Long taxInputId);
}
