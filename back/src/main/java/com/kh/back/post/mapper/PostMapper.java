package com.post.post.mapper;

import com.post.post.dto.FileMetaDataDto;
import com.post.post.dto.PostDto;
import com.post.post.dto.PostImageDto;
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

    List<PostImageDto> findImagesByPostId(
            @Param("postId") Long postId
    );


    void save(PostDto post);

    void delete(Long postId);

    void update(PostDto post);

    void deleteById(@Param("postId") Long postId);

    void saveImage(PostImageDto postImage);

    void savePostImage(
            @Param("postId") Long postId,
            @Param("uploadId") Long uploadId
    );

    void deleteByPostIdAndUploadId(
            @Param("postId") Long postId,
            @Param("uploadId") Long uploadId
    );

    void deleteImage(
            @Param("uploadId") Long uploadId
    );

    void updateImageOrder(
            @Param("uploadId") Long uploadId,
            @Param("imageOrder") int imageOrder
    );

    void saveFileMeta(FileMetaDataDto metadataDto);
}
