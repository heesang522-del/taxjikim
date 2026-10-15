package com.kh.back.TaxUpload.mapper;

import com.kh.back.TaxUpload.dto.TaxTransactionDto;
import com.kh.back.post.dto.FileMetadataDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface TaxMapper {
    Long lockOwnedPdf(@Param("fileId") Long fileId, @Param("userId") Long userId);
    TaxTransactionDto findReviewedTransaction(@Param("fileId") Long fileId, @Param("itemIndex") int itemIndex, @Param("userId") Long userId);
    void insertReviewedTransaction(TaxTransactionDto transaction);

    void insertPdfFile(FileMetadataDto file);

    Long findTaxInputIdByUserAndYear(@Param("userId") Long userId, @Param("taxYear") int taxYear);

    // Map을 사용하여 별도의 DTO 없이 데이터 전달 (useGeneratedKeys로 PK 반환)
    void insertTaxInput(Map<String, Object> params);

    void insertTaxTransactions(@Param("list") List<TaxTransactionDto> list);
}
