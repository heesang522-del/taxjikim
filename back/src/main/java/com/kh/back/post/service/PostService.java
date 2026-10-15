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
    private final String TEMP_DIR = System.getProperty("user.dir") + "/uploads/temp/";
    private final String UPLOAD_DIR = System.getProperty("user.dir") + "/uploads/files/";

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
        // 업로드 경로와 청크 번호를 검증한 뒤 파일 시스템에 기록한다.
        if (dto.getUploadId() == null || !dto.getUploadId().matches("[A-Za-z0-9_-]+")
                || dto.getTotalChunks() < 1 || dto.getChunkIndex() < 0
                || dto.getChunkIndex() >= dto.getTotalChunks()
                || dto.getFile() == null || dto.getFile().isEmpty()
                || dto.getOriginalName() == null) {
            throw new IllegalArgumentException("청크 업로드 입력값을 확인해주세요.");
        }
        // 클라이언트 검사를 우회해도 사진·동영상 외 파일은 서버에서 거부한다.
        if (!dto.getOriginalName().toLowerCase(java.util.Locale.ROOT)
                .matches(".*\\.(jpg|jpeg|png|gif|webp|mp4|webm|mov)$")) {
            throw new IllegalArgumentException("사진·동영상만 업로드할 수 있습니다. PDF·압축·실행파일은 차단됩니다.");
        }
        File tempDirFile = new File(TEMP_DIR + dto.getUploadId());
        if (!tempDirFile.exists()) {
            tempDirFile.mkdirs();
        }

        File chunkFile = new File(tempDirFile, "chunk_" + dto.getChunkIndex());
        dto.getFile().transferTo(chunkFile);

        File[] chunks = tempDirFile.listFiles((dir, name) -> name.startsWith("chunk_"));
        if (chunks != null && chunks.length == dto.getTotalChunks()) {

            int extensionIndex = dto.getOriginalName().lastIndexOf('.');
            String ext = extensionIndex < 0 ? "" : dto.getOriginalName().substring(extensionIndex);
            if (!ext.matches("(\\.[A-Za-z0-9]+)?")) {
                throw new IllegalArgumentException("지원하지 않는 파일 확장자입니다.");
            }
            String savedFileName = UUID.randomUUID().toString() + ext;
            File targetFile = new File(UPLOAD_DIR + savedFileName);

            if (!targetFile.getParentFile().exists()) {
                targetFile.getParentFile().mkdirs();
            }

            try (BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(targetFile))) {
                for (int i = 0; i < dto.getTotalChunks(); i++) {
                    File cFile = new File(tempDirFile, "chunk_" + i);
                    Files.copy(cFile.toPath(), bout);
                }
            }

            // 확장자만 바꾼 실행파일을 막기 위해 병합 파일의 형식 식별 바이트도 검사한다.
            byte[] header;
            try (java.io.InputStream input = Files.newInputStream(targetFile.toPath())) {
                header = input.readNBytes(16);
            }
            String lowerExt = ext.toLowerCase(java.util.Locale.ROOT);
            String signature = new String(header, java.nio.charset.StandardCharsets.ISO_8859_1);
            boolean validMedia = switch (lowerExt) {
                case ".jpg", ".jpeg" -> header.length >= 3 && (header[0] & 255) == 255
                        && (header[1] & 255) == 216 && (header[2] & 255) == 255;
                case ".png" -> header.length >= 8 && java.util.Arrays.equals(
                        java.util.Arrays.copyOf(header, 8), new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
                case ".gif" -> signature.startsWith("GIF87a") || signature.startsWith("GIF89a");
                case ".webp" -> signature.startsWith("RIFF") && signature.length() >= 12
                        && signature.substring(8, 12).equals("WEBP");
                case ".mp4", ".mov" -> signature.length() >= 12 && signature.substring(4, 8).equals("ftyp");
                case ".webm" -> header.length >= 4 && java.util.Arrays.equals(
                        java.util.Arrays.copyOf(header, 4), new byte[]{26, 69, (byte) 223, (byte) 163});
                default -> false;
            };
            if (!validMedia) {
                Files.deleteIfExists(targetFile.toPath());
                throw new IllegalArgumentException("파일 내용이 허용된 사진·동영상 형식과 일치하지 않습니다.");
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

            // 익명 테스트는 병합 파일만 저장하고 회원·게시글이 필요한 DB 저장은 생략한다.
            if (!dto.isAnonymous()) {
                postMapper.saveFileMeta(fileMetaDataDto);
            }
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
