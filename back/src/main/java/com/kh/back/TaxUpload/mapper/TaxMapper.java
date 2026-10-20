package com.kh.back.TaxUpload.mapper;

import com.kh.back.TaxUpload.dto.TaxTransactionDto;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaxMapper {

    int insertTransaction(
            TaxTransactionDto transaction
    );

    List<TaxTransactionDto> findByUserId(
            @Param("userId") Long userId
    );
}