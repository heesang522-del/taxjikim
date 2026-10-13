package com.kh.back.post.service;

import com.kh.back.common.dto.PageRequestDto;
import com.kh.back.common.dto.PageResponseDto;
import com.kh.back.post.dto.ChunkDto;
import com.kh.back.post.dto.PostDto;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

// 호출자는 업무 인터페이스를 사용하고 구현체가 처리 흐름을 담당한다.
public interface PostService {
    void createPost(String title, String place, String content, Long transportCost, Long foodCost, Long otherCost,
                    String savedFileNamesJson, String savedFileNames);

    void updatePost(Long postId, String title, String place, String content, Long transportCost, Long foodCost,
                    Long otherCost, List<Long> deleteImageIds, String savedFileNamesJson, String savedFileNames);
    String processChunkUpload(ChunkDto dto) throws IOException;

    PostDto findById(Long postId);

    void save(PostDto postDto, List<MultipartFile> mediaFiles) throws IOException;

    void saveWithFiles(PostDto postDto, List<String> savedFileNames);

    void updateWithFiles(PostDto postDto, List<Long> deleteImageIds, List<String> savedFileNames);

    PageResponseDto<PostDto> getPostPage(PageRequestDto pageRequest, String sort, String keyword);

    List<PostDto> findPage(String sort, String keyword, int offset, int size);

    List<PostDto> findAll(String sort, String keyword);

    void delete(Long postId);

    void deleteById(Long postId);

    int countAll(String keyword);
}
