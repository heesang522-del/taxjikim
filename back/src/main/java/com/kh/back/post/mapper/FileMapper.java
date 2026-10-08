package com.kh.back.post.mapper;
import org.apache.ibatis.annotations.Mapper;
import com.kh.back.post.dto.FileMetaDataDto;

@Mapper
public interface FileMapper {
    void insertFileMeta(FileMetaDataDto fileMeta);
    FileMetaDataDto selectFileById(Long id);
}
