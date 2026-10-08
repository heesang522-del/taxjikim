package com.kh.back.company.service;

import com.kh.back.common.exception.ApiException;
import com.kh.back.company.dto.CompanyDto;
import com.kh.back.company.mapper.CompanyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor // final 필드를 받는 생성자를 만들어 준다 → 생성자 주입
public class CompanyServiceImpl implements CompanyService {

    private final CompanyMapper companyMapper;

    @Override
    public List<CompanyDto> getCompanies(int page, int size) {
        int offset = (page - 1) * size; // 1페이지 → 0번째 행부터
        return companyMapper.findAll(offset, size);
    }

    @Override
    public int countCompanies() {
        return companyMapper.countAll();
    }

    @Override
    public CompanyDto getCompany(String code) {
        CompanyDto company = companyMapper.findByCode(code);
        if (company == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "종목을 찾을 수 없습니다: " + code);
        }
        return company;
    }
}