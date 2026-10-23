package com.kh.back.tax.dto;

/** 최종세액이 아니라 과세표준 계산 과정의 값을 전달한다. */
public record TaxBaseResultDto(
        long incomeBeforeDonation, // 기부금 반영 전 사업소득. 적자도 보존한다.
        long incomeAfterDonation, // 기부금 반영 후 금액. 이 초안에서는 0원 미만을 0원으로 처리한다.
        long taxableIncome // 소득공제까지 반영한 과세표준.
) {}
