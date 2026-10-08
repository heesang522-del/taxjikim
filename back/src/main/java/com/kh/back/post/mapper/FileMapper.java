package com.post.post.mapper;
import org.apache.ibatis.annotations.Mapper;
import com.post.post.dto.FileMetaDataDto;

@Mapper
public interface FileMapper {
    void insertFileMeta(FileMetaDataDto fileMeta);
    FileMetaDataDto selectFileById(Long id);
}
