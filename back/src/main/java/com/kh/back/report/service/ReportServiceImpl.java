package com.kh.back.report.service;

import com.kh.back.comment.dto.CommentDto;
import com.kh.back.comment.mapper.CommentMapper;
import com.kh.back.common.exception.ApiException;
import com.kh.back.post.dto.PostDto;
import com.kh.back.post.mapper.PostMapper;
import com.kh.back.report.dto.ReportDto;
import com.kh.back.report.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {
    private static final String TARGET_TYPE_POST = "POST";
    private static final String TARGET_TYPE_COMMENT = "COMMENT";
    private static final String RESULT_DELETE = "DELETE";
    private static final String RESULT_REJECT = "REJECT";
    private static final String STATUS_PENDING = "PENDING";

    private final ReportMapper reportMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createPostReport(Long postId, Long reporterId, String reasonDetail) {
        String reason = requireReasonDetail(reasonDetail);

        PostDto post = requirePost(postId);
        if (post.getUserId().equals(reporterId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_REPORT_OWN_POST", "본인 글은 신고할 수 없습니다.");
        }

        ReportDto report = new ReportDto();
        report.setTargetType(TARGET_TYPE_POST);
        report.setTargetId(postId);
        report.setTargetUserId(post.getUserId());
        report.setReasonDetail(reason);
        report.setReporterId(reporterId);

        try {
            reportMapper.insert(report);
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REPORTED", "이미 신고한 게시글입니다.");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createCommentReport(Long postId, Long commentId, Long reporterId, String reasonDetail) {
        String reason = requireReasonDetail(reasonDetail);

        CommentDto comment = requireComment(postId, commentId);
        if (comment.getUserId().equals(reporterId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_REPORT_OWN_COMMENT", "본인 댓글은 신고할 수 없습니다.");
        }

        ReportDto report = new ReportDto();
        report.setTargetType(TARGET_TYPE_COMMENT);
        report.setTargetId(commentId);
        report.setTargetUserId(comment.getUserId());
        report.setReasonDetail(reason);
        report.setReporterId(reporterId);

        try {
            reportMapper.insert(report);
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REPORTED", "이미 신고한 댓글입니다.");
        }
    }

    @Override
    public List<ReportDto> getReports() {
        return reportMapper.findAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolveReport(Long reportId, String result, Long adminId) {
        if (!RESULT_DELETE.equals(result) && !RESULT_REJECT.equals(result)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "처리 결과는 DELETE 또는 REJECT여야 합니다.");
        }

        ReportDto report = reportMapper.findById(reportId);
        if (report == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "신고를 찾을 수 없습니다.");
        }
        if (!STATUS_PENDING.equals(report.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "REPORT_ALREADY_PROCESSED", "이미 처리된 신고입니다.");
        }

        if (RESULT_DELETE.equals(result)) {
            if (TARGET_TYPE_POST.equals(report.getTargetType())) {
                postMapper.deleteByAdmin(report.getTargetId());
            } else if (TARGET_TYPE_COMMENT.equals(report.getTargetType())) {
                commentMapper.deleteByAdmin(report.getTargetId());
            }
        }

        reportMapper.resolve(reportId, result, adminId);

        // 같은 대상(글·댓글)에 대기 중인 다른 신고도 같은 삭제 처리로 함께 닫는다.
        if (RESULT_DELETE.equals(result)) {
            reportMapper.resolveOthersForTarget(report.getTargetType(), report.getTargetId(), result, adminId);
        }
    }

    private String requireReasonDetail(String reasonDetail) {
        if (reasonDetail == null || reasonDetail.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "신고 사유를 입력해 주세요.");
        }
        return reasonDetail.trim();
    }

    private PostDto requirePost(Long postId) {
        PostDto post = postMapper.findById(postId);
        if (post == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "게시글을 찾을 수 없습니다.");
        }
        return post;
    }

    private CommentDto requireComment(Long postId, Long commentId) {
        CommentDto comment = commentMapper.findById(commentId);
        if (comment == null || !comment.getPostId().equals(postId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다.");
        }
        return comment;
    }
}
