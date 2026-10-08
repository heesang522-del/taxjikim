package com.kh.back.company.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * company 테이블 1행. 필드명이 그대로 JSON 키가 된다.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class CompanyDto {

    private String code;
    private String name;
    private String sectorCode;
    private String sectorName;
    private String market;
    private Long marketCap;
}