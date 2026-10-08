package com.kh.back.company.service;

import com.kh.back.company.dto.CompanyDto;

import java.util.List;

public interface CompanyService {

    List<CompanyDto> getCompanies(int page, int size);

    int countCompanies();

    CompanyDto getCompany(String code);
}