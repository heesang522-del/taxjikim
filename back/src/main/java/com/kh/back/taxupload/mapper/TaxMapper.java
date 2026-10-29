package com.kh.back.taxupload.mapper;

import com.kh.back.taxupload.dto.TaxTransactionDto;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxInputRequest;
import com.kh.back.taxupload.dto.TaxTransactionDto.TaxResultRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaxMapper {

    int insertInput(TaxInputRequest input);

    TaxInputRequest findInput(
            @Param("taxInputId") Long taxInputId
    );

    TaxInputRequest lockInput(
            @Param("taxInputId") Long taxInputId
    );

    List<TaxInputRequest> findSavedInputs(
            @Param("userId") Long userId,
            @Param("taxYear") Integer taxYear
    );

    int updateInput(TaxInputRequest input);

    int deleteInput(
            @Param("taxInputId") Long taxInputId
    );

    int touchInput(
            @Param("taxInputId") Long taxInputId
    );

    int insertTransaction(TaxTransactionDto transaction);

    // 거래번호가 없으면 분석의 전체 거래, 있으면 특정 거래를 조회한다.
    List<TaxTransactionDto> findTransactions(
            @Param("taxInputId") Long taxInputId,
            @Param("transactionId") Long transactionId
    );

    int updateCategory(
            @Param("taxInputId") Long taxInputId,
            @Param("transactionId") Long transactionId,
            @Param("category") String category
    );

    int deleteTransaction(
            @Param("taxInputId") Long taxInputId,
            @Param("transactionId") Long transactionId
    );

    TaxResultRow findResult(
            @Param("taxInputId") Long taxInputId
    );

    int deleteResult(
            @Param("taxInputId") Long taxInputId
    );

    int insertProcessLog(
            @Param("taxInputId") Long taxInputId,
            @Param("userId") Long userId,
            @Param("processType") String processType,
            @Param("status") String status,
            @Param("failReason") String failReason
    );
}