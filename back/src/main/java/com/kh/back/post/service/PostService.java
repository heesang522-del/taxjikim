package com.kh.back.post.service;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.post.dto.ChunkDto;
import com.kh.back.post.dto.PostDto;

import java.io.IOException;
import java.util.List;

public interface PostService {

    /**
     * 파일 조각을 저장하고 모든 조각이 모이면 병합한다.
     *
     * @return 병합 완료 시 저장 파일명, 미완료 시 null
     */
    String processChunkUpload(
            CustomUserDetails principal,
            ChunkDto dto
    ) throws IOException;

    /**
     * 게시글과 첨부파일을 조회한다.
     * 비공개 Q&A는 작성자 본인과 관리자만 조회한다.
     */
    PostDto findById(Long postId);

    /**
     * 검색·정렬·게시판 종류·페이지 조건으로 목록을 조회한다.
     */
    ApiResponse<List<PostDto>> getPostPage(
            String sort,
            String keyword,
            int page,
            String postType
    );

    /**
     * 게시글을 저장하고 업로드된 첨부파일을 연결한다.
     */
    void saveWithFiles(
            PostDto post,
            List<String> savedFileNames
    );

    /**
     * 게시글을 수정하고 첨부파일 삭제·추가를 처리한다.
     */
    void updateWithFiles(
            PostDto post,
            List<Long> deleteFileIds,
            List<String> savedFileNames
    );

    /**
     * 권한을 확인하고 게시글을 소프트 삭제한다.
     */
    void deleteById(Long postId);
}