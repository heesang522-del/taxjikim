package com.kh.back.comment.service;


import com.kh.back.comment.dto.CommentDto;

import java.util.List;

public interface CommentService {
    // 댓글 리스트 불러오기
    List<CommentDto> findByPostId(Long postId);
    // 댓글 작성
    void create(Long postId, Long userId, String writerNickname, String content, Long parentCommentId);
    // 댓글 수정
    void update(Long postId, Long commentId, Long userId, String content);
    // 댓글 삭제
    void delete(Long postId, Long commentId, Long userId);

}
