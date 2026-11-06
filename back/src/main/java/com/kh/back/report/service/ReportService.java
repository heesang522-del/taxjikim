package com.kh.back.report.service;

import com.kh.back.report.dto.ReportDto;

import java.util.List;

public interface ReportService {
    // 게시글 신고를 접수한다. 본인 글 신고, 중복 신고는 막는다.
    void createPostReport(Long postId, Long reporterId, String reasonDetail);

    // 댓글 신고를 접수한다. 본인 댓글 신고, 중복 신고는 막는다.
    void createCommentReport(Long postId, Long commentId, Long reporterId, String reasonDetail);

    // 관리자 신고 목록. 대기·처리 구분은 화면에서 status로 나눈다.
    List<ReportDto> getReports();

    // 신고를 처리한다. result는 DELETE(삭제) 또는 REJECT(반려)이며,
    // DELETE면 대상 게시글·댓글을 관리자 삭제로 내리고 같은 대상의 대기 중인 다른 신고도 함께 처리한다.
    void resolveReport(Long reportId, String result, Long adminId);
}
