package com.kh.back.post.mapper;

import com.kh.back.post.dto.FileMetadataDto;
import com.kh.back.post.dto.PostDto;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostMapper {

    List<PostDto> findPage(
            @Param("sort") String sort,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("size") int size,
            @Param("boardType") String boardType,
            @Param("currentUserId") Long currentUserId,
            @Param("isAdmin") boolean isAdmin
    );

    int countAll(
            @Param("keyword") String keyword,
            @Param("boardType") String boardType,
            @Param("currentUserId") Long currentUserId,
            @Param("isAdmin") boolean isAdmin
    );

    PostDto findById(
            @Param("postId") Long postId
    );

    PostDto lockById(
            @Param("postId") Long postId
    );

    void save(PostDto post);

    void update(PostDto post);

    void updateAnswerStatus(
            @Param("postId") Long postId,
            @Param("answerStatus") String answerStatus
    );

    void deleteById(
            @Param("postId") Long postId
    );

    List<FileMetadataDto> findFilesByPostId(
            @Param("postId") Long postId
    );

    void savePendingFile(
            FileMetadataDto file
    );

    FileMetadataDto lockPendingFile(
            @Param("storedFileName") String storedFileName
    );

    int attachPendingFile(
            @Param("fileId") Long fileId,
            @Param("postId") Long postId
    );

    int deleteFileMeta(
            @Param("fileId") Long fileId,
            @Param("postId") Long postId
    );
}