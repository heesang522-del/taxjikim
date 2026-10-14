package com.kh.back.post.service;

import com.kh.back.common.dto.PageMeta;
import com.kh.back.common.validation.PostValidator;
import com.kh.back.post.dto.ChunkDto;
import com.kh.back.post.dto.FileMetadataDto;
import com.kh.back.post.dto.PostDto;
import com.kh.back.post.mapper.PostMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PostService {

    // 청크 및 최종 파일 저장 경로 상수 선언
    private final String TEMP_DIR = "C:/uploads/temp/";
    private final String UPLOAD_DIR = "C:/uploads/files/";

    private final PostMapper postMapper;
    private final PostValidator postValidator;

    private static final String WEB_PREFIX = "/uploads/post";

    public PostService(
            PostMapper postMapper,
            PostValidator postValidator
    ) {
        this.postMapper = postMapper;
        this.postValidator = postValidator;
    }

    /**
     * 파일 청크 처리, 최종 병합 및 DB 메타데이터 저장 (FILE_META 연동)
     */
    @Transactional
    public String processChunkUpload(ChunkDto dto) throws IOException {
        File tempDirFile = new File(TEMP_DIR + dto.getUploadId());
        if (!tempDirFile.exists()) {
            tempDirFile.mkdirs();
        }

        File chunkFile = new File(tempDirFile, "chunk_" + dto.getChunkIndex());
        dto.getFile().transferTo(chunkFile);

        File[] chunks = tempDirFile.listFiles((dir, name) -> name.startsWith("chunk_"));
        if (chunks != null && chunks.length == dto.getTotalChunks()) {

            String ext = dto.getOriginalName().substring(dto.getOriginalName().lastIndexOf("."));
            String savedFileName = UUID.randomUUID().toString() + ext;
            File targetFile = new File(UPLOAD_DIR + savedFileName);

            if (!targetFile.getParentFile().exists()) {
                targetFile.getParentFile().mkdirs();
            }

            try (BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(targetFile, true))) {
                for (int i = 0; i < dto.getTotalChunks(); i++) {
                    File cFile = new File(tempDirFile, "chunk_" + i);
                    Files.copy(cFile.toPath(), bout);
                }
            }

            for (int i = 0; i < dto.getTotalChunks(); i++) {
                File cFile = new File(tempDirFile, "chunk_" + i);
                if (cFile.exists()) {
                    cFile.delete();
                }
            }
            if (tempDirFile.exists()) {
                tempDirFile.delete();
            }

            FileMetadataDto fileMetaDataDto = new FileMetadataDto();
            fileMetaDataDto.setOriginalFileName(dto.getOriginalName());
            fileMetaDataDto.setStoredFileName(savedFileName);
            fileMetaDataDto.setFilePath(UPLOAD_DIR + savedFileName);
            fileMetaDataDto.setFileSize(targetFile.length());
            fileMetaDataDto.setFileExtension(ext.replace(".", ""));
            fileMetaDataDto.setFileType("POST");

            postMapper.saveFileMeta(fileMetaDataDto);
            return savedFileName;
        }

        return null;
    }

    /**
     * 게시글 단건 조회 (FILE_META 통합 파일 리스트 포함)
     */
    public PostDto findById(Long postId) {
        PostDto post = postMapper.findById(postId);
        if (post != null) {
            List<FileMetadataDto> files = postMapper.findFilesByPostId(postId);
            post.setFiles(files);
        }
        return post;
    }

    @Transactional
    public void saveWithFiles(PostDto postDto, List<String> savedFileNames) {
        postMapper.save(postDto);
        Long postId = postDto.getPostId();

        if (savedFileNames != null && !savedFileNames.isEmpty()) {
            for (String savedFileName : savedFileNames) {
                int underscoreIndex = savedFileName.indexOf("_");
                String originName = (underscoreIndex != -1) ? savedFileName.substring(underscoreIndex + 1) : savedFileName;
                String ext = savedFileName.substring(savedFileName.lastIndexOf(".") + 1);

                FileMetadataDto fileDto = new FileMetadataDto();
                fileDto.setPostId(postId);
                fileDto.setOriginalFileName(originName);
                fileDto.setStoredFileName(savedFileName);
                fileDto.setFilePath(UPLOAD_DIR + savedFileName);
                fileDto.setFileExtension(ext);
                fileDto.setFileSize(0L);
                fileDto.setFileType("POST");

                postMapper.saveFileMeta(fileDto);
            }
        }
    }

    @Transactional
    public void updateWithFiles(PostDto postDto, List<Long> deleteFileIds, List<String> savedFileNames) {
        Long postId = postDto.getPostId();
        postMapper.update(postDto);

        if (deleteFileIds != null && !deleteFileIds.isEmpty()) {
            for (Long fileId : deleteFileIds) {
                postMapper.deleteFileMeta(fileId);
            }
        }

        if (savedFileNames != null && !savedFileNames.isEmpty()) {
            for (String savedFileName : savedFileNames) {
                int underscoreIndex = savedFileName.indexOf("_");
                String originName = (underscoreIndex != -1) ? savedFileName.substring(underscoreIndex + 1) : savedFileName;
                String ext = savedFileName.substring(savedFileName.lastIndexOf(".") + 1);

                FileMetadataDto fileDto = new FileMetadataDto();
                fileDto.setPostId(postId);
                fileDto.setOriginalFileName(originName);
                fileDto.setStoredFileName(savedFileName);
                fileDto.setFilePath(UPLOAD_DIR + savedFileName);
                fileDto.setFileExtension(ext);
                fileDto.setFileSize(0L);
                fileDto.setFileType("POST");

                postMapper.saveFileMeta(fileDto);
            }
        }
    }

    // 프로젝트 표준 PageMeta 및 offset 계산 방식 적용
    public Map<String, Object> getPostPage(int page, int size, String sort, String keyword) {
        if (page < 1) page = 1;
        int totalCount = postMapper.countAll(keyword);

        // PageMeta 객체 생성 (total, size, page 기반)
        PageMeta pageMeta = PageMeta.of(page, size, totalCount);

        // MySQL LIMIT 오프셋 계산 (0부터 시작하므로 page - 1)
        int offset = (page - 1) * size;
        List<PostDto> list = postMapper.findPage(sort, keyword, offset, size);

        for (PostDto post : list) {
            post.setFiles(postMapper.findFilesByPostId(post.getPostId()));
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("content", list);
        result.put("paging", pageMeta);
        return result;
    }

    public List<PostDto> findPage(String sort, String keyword, int offset, int size) {
        List<PostDto> list = postMapper.findPage(sort, keyword, offset, size);
        for (PostDto post : list) {
            post.setFiles(postMapper.findFilesByPostId(post.getPostId()));
        }
        return list;
    }

    public List<PostDto> findAll(String sort, String keyword) {
        List<PostDto> list = postMapper.findAll(sort);
        for (PostDto post : list) {
            post.setFiles(postMapper.findFilesByPostId(post.getPostId()));
        }
        return list;
    }

    public void delete(Long postId) {
        deleteById(postId);
    }

    @Transactional
    public void deleteById(Long postId) {
        postMapper.deleteById(postId);
    }

    public int countAll(String keyword) {
        return postMapper.countAll(keyword);
    }
}