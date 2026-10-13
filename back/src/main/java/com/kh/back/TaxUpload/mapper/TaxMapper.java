package com.kh.back.TaxUpload.mapper;

import com.kh.back.TaxUpload.dto.TaxTransactionDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaxMapper {
    // TAX_TRANSACTIONS 테이블에 대량 데이터 일괄 삽입 (Batch Insert)
    void insertTaxTransactions(@Param("list") List<TaxTransactionDto> list);

}