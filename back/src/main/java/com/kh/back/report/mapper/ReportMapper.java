package com.kh.back.report.mapper;

import com.kh.back.report.dto.ReportDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReportMapper {

    int insert(ReportDto report);

    List<ReportDto> findAll();

    ReportDto findById(@Param("reportId") Long reportId);

    int resolve(
            @Param("reportId") Long reportId,
            @Param("result") String result,
            @Param("processedBy") Long processedBy
    );

    // 같은 신고 대상에 대기 중인 다른 신고도 같은 결과로 함께 처리한다.
    int resolveOthersForTarget(
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("result") String result,
            @Param("processedBy") Long processedBy
    );
}
