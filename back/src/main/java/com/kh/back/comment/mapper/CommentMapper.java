package com.kh.back.comment.mapper;

import com.kh.back.comment.dto.CommentDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface CommentMapper {
    List<CommentDto> findByPostId(@Param("postId") Long postId);
    void insert(CommentDto comment);
    int softDelete(@Param("postId") Long postId, @Param("commentId") Long commentId,
                   @Param("userId") Long userId);
}
