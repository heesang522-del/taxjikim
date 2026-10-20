
package com.kh.back.TaxUpload.mapper;

import com.kh.back.TaxUpload.dto.TaxTransactionDto;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaxMapper {

    /**
     * 거래 저장
     */
    int insertTransaction(
            TaxTransactionDto transaction
    );

    /**
     * 로그인 사용자 거래 전체 조회
     */
    List<TaxTransactionDto> findByUserId(
            @Param("userId") Long userId
    );

    /**
     * 특정 사용자 거래 단건 조회
     */
    TaxTransactionDto findByTransactionIdAndUserId(
            @Param("transactionId") Long transactionId,
            @Param("userId") Long userId
    );

    /**
     * 사용자별 거래 건수 조회
     */
    long countByUserId(
            @Param("userId") Long userId
    );
}
