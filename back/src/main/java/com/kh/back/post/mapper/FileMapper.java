package com.kh.back.post.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.kh.back.post.dto.FileMetadataDto;

@Mapper
public interface FileMapper {
    void insertFileMeta(FileMetadataDto fileMeta);
    FileMetadataDto selectFileById(Long id);
}
