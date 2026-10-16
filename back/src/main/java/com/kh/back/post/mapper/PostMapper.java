package com.kh.back.post.mapper;

import com.kh.back.post.dto.FileMetadataDto;
import com.kh.back.post.dto.PostDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostMapper {
    List<PostDto> findAll(@Param("sort") String sort);

    List<PostDto> findPage(
            @Param("sort") String sort,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("size") int size
    );

    int countAll(@Param("keyword") String keyword);

    PostDto findById(@Param("postId") Long postId);

    // 🌟 FILE_META 기반 통합 파일 목록 조회
    List<FileMetadataDto> findFilesByPostId(@Param("postId") Long postId);

    void save(PostDto post);

    void update(PostDto post);

    void deleteById(@Param("postId") Long postId);

    // 🌟 FILE_META 관련 저장 및 삭제 메서드 통일
    void saveFileMeta(FileMetadataDto metadataDto);

    void deleteFileMeta(@Param("fileId") Long fileId);
}