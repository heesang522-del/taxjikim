package com.kh.back.tax.service;

import com.kh.back.tax.dto.TaxBaseResultDto;
import com.kh.back.tax.dto.TaxCalculationInputDto;


public interface TaxCalculationService {
    /** 수입에서 경비·기부금·소득공제를 반영해 과세표준을 계산한다. */
    TaxBaseResultDto calculateTaxBase(TaxCalculationInputDto input);
}
