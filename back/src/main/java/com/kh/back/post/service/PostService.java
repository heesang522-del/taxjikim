package com.kh.back.post.service;

import com.kh.back.post.dto.ChunkDto;
import com.kh.back.post.dto.PostDto;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface PostService {

    String processChunkUpload(
            ChunkDto dto
    ) throws IOException;

    PostDto findById(Long postId);

    Map<String, Object> getPostPage(
            int page,
            int size,
            String sort,
            String keyword
    );

    List<PostDto> findPage(
            String sort,
            String keyword,
            int offset,
            int size
    );

    List<PostDto> findAll(
            String sort,
            String keyword
    );

    int countAll(String keyword);

    void saveWithFiles(
            PostDto post,
            List<String> savedFileNames
    );

    void updateWithFiles(
            PostDto post,
            List<Long> deleteFileIds,
            List<String> savedFileNames
    );

    void delete(Long postId);

    void deleteById(Long postId);

    void requireAuthor(
            Long postId,
            Long userId
    );
}