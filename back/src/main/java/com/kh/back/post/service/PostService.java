package com.kh.back.post.service;

import com.kh.back.post.dto.ChunkDto;
import com.kh.back.post.dto.PostDto;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** 게시글 기능의 호출 규약이며 실제 처리 로직은 PostServiceImpl에 둔다. */
public interface PostService {
    // 파일 청크 업로드 및 병합
    String processChunkUpload(ChunkDto dto) throws IOException;

    // 게시글 조회와 검색, 페이지 정보
    PostDto findById(Long postId);
    Map<String, Object> getPostPage(int page, int size, String sort, String keyword);
    List<PostDto> findPage(String sort, String keyword, int offset, int size);
    List<PostDto> findAll(String sort, String keyword);
    int countAll(String keyword);

    // 게시글 및 첨부파일 등록·수정
    void saveWithFiles(PostDto postDto, List<String> savedFileNames);
    void updateWithFiles(PostDto postDto, List<Long> deleteFileIds, List<String> savedFileNames);

    // 기존 삭제 진입점과 소프트 삭제, 작성자 확인을 모두 유지한다.
    void delete(Long postId);
    void deleteById(Long postId);
    void requireAuthor(Long postId, Long userId);
}
