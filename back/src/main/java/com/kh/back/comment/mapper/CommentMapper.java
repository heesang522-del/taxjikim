package com.kh.back.comment.mapper;

import com.kh.back.comment.dto.CommentDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface CommentMapper {

    // 댓글 리스트 불러오기
    List<CommentDto> findByPostId(@Param("postId") Long postId);

    // 대댓글 작성에 사용되는 parentcommentId 불러오기
    CommentDto findById(@Param("commentId") Long commentId);

    // 댓글 작성
    void insert(CommentDto comment);

    // 대댓글을 위한 소프트 delete
    int softDelete(@Param("postId") Long postId, @Param("commentId") Long commentId,
                   @Param("userId") Long userId);

    // 신고 처리로 관리자가 지울 때만 쓴다. 작성자 제한 없이 지운다.
    int deleteByAdmin(@Param("commentId") Long commentId);

    // 댓글이 삭제됐어도 소속 게시글 ID는 그대로 조회한다(POST_ID는 삭제와 무관하게 유지됨).
    // 신고 처리 결과 알림에서 어느 게시글로 이동시킬지 정할 때 쓴다.
    Long findPostIdById(@Param("commentId") Long commentId);

    //댓글 수정
    int update(@Param("postId") Long postId, @Param("commentId") Long commentId,
               @Param("userId") Long userId, @Param("content") String content);
}
