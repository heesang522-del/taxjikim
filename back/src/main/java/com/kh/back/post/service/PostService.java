package com.kh.back.post.service;

import com.kh.back.common.dto.ApiResponse;
import com.kh.back.post.dto.ChunkDto;
import com.kh.back.post.dto.PostDto;

import java.io.IOException;
import java.util.List;

public interface PostService {
    // 첨부파일을 조각(chunk) 단위로 받아 임시 저장하고, 마지막 조각이면 합쳐서 최종 저장 파일명을 반환한다.
    String processChunkUpload(ChunkDto dto) throws IOException;

    // 단건 상세 조회(게시글 + 첨부파일). QNA 비공개 글 접근 제어도 여기서 함께 검사한다.
    PostDto findById(Long postId);

    // 목록 조회. 페이지 크기·offset 변환·전체 건수 조회·PageMeta 조립까지 전부 여기서 끝내고
    // 컨트롤러가 그대로 반환할 수 있는 ApiResponse를 돌려준다. page는 1부터 시작한다.
    ApiResponse<List<PostDto>> getPostPage(String sort, String keyword, int page, String postType);

    // 게시글을 저장하고, 업로드된 첨부파일을 함께 연결한다.
    void saveWithFiles(PostDto post, List<String> savedFileNames);

    // 게시글을 수정하고, 삭제 요청된 첨부파일 제거와 새 첨부파일 연결을 함께 처리한다.
    void updateWithFiles(PostDto post, List<Long> deleteFileIds, List<String> savedFileNames);

    // 게시글을 소프트 삭제한다. 작성자 본인 또는 관리자만 가능하다.
    void deleteById(Long postId);
}