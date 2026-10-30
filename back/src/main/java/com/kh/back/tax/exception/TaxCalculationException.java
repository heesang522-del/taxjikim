package com.kh.back.tax.exception;

import com.kh.back.common.exception.ApiException;
import org.springframework.http.HttpStatus;

// 내부 계산 실패를 공통 오류 응답으로 전달하고 원인 예외를 보존한다.
public class TaxCalculationException extends ApiException {

    // 계산 오류의 원인을 유지하며 외부에는 공통 안내만 전달한다.
    public TaxCalculationException(Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "TAX_CALCULATION_FAILED", "내부 세금 계산에 실패했습니다.");
        initCause(cause);
    }
}
