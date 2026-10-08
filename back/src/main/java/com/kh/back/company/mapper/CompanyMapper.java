package com.kh.back.company.mapper;

import com.kh.back.company.dto.CompanyDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CompanyMapper {

    // 파라미터가 2개 이상이면 @Param 으로 XML 에서 쓸 이름을 붙인다
    List<CompanyDto> findAll(@Param("offset") int offset, @Param("size") int size);

    int countAll();

    CompanyDto findByCode(String code);
}