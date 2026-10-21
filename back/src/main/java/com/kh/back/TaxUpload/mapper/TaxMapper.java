package com.kh.back.TaxUpload.mapper;

import com.kh.back.TaxUpload.dto.TaxTransactionDto;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaxMapper {

    Long findActiveUserId(
            @Param("userId") Long userId
    );

    Long findTaxInputIdByUserAndYear(
            @Param("userId") Long userId,
            @Param("taxYear") int taxYear
    );

    int insertTransaction(
            TaxTransactionDto transaction
    );

    List<TaxTransactionDto> findByUserId(
            @Param("userId") Long userId
    );

    TaxTransactionDto findById(
            @Param("transactionId") Long transactionId
    );

    TaxTransactionDto lockById(
            @Param("transactionId") Long transactionId
    );

    int updateTransaction(
            TaxTransactionDto transaction
    );

    int softDeleteTransaction(
            @Param("transactionId") Long transactionId,
            @Param("userId") Long userId
    );
}