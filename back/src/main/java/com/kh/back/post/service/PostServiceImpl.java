package com.kh.back.post.service;

import com.kh.back.common.dto.PageRequestDto;
import com.kh.back.common.dto.PageResponseDto;
import com.kh.back.common.util.FileUtil;
import com.kh.back.common.util.SavedFile;
import com.kh.back.common.validation.PostValidator;
import com.kh.back.post.dto.ChunkDto;
import com.kh.back.post.dto.PostDto;
import com.kh.back.post.dto.PostImageDto;
import com.kh.back.post.mapper.PostMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

@Service
public class PostServiceImpl implements PostService {

    // � 1. 청크 및 최종 파일 저장 경로 상수 선언
    private static final String TEMP_DIR = FileUtil.getUploadDirectory("temp") + File.separator;
    private static final String UPLOAD_DIR = FileUtil.getUploadDirectory("files") + File.separator;

    private final PostMapper postMapper;
    private final FileUtil fileUtil;
    private final PostValidator postValidator;

    private static final String POST_SUB_DIR = "post";

    private static final String WEB_PREFIX = "/uploads/post";

    public PostServiceImpl(
            PostMapper postMapper,
            FileUtil fileUtil,
            PostValidator postValidator
    ) {
        this.postMapper = postMapper;
        this.fileUtil = fileUtil;
        this.postValidator = postValidator;
    }

    /**
     * 파일 청크 처리, 최종 병합 및 DB 메타데이터 저장
     */
    @Transactional
    @Override
    public String processChunkUpload(ChunkDto dto) throws IOException {
        File tempDirFile = new File(TEMP_DIR + dto.getUploadId());
        if (!tempDirFile.exists()) {
            tempDirFile.mkdirs();
        }

        // 1. 현재 청크 조각 임시 저장
        File chunkFile = new File(tempDirFile, "chunk_" + dto.getChunkIndex());
        dto.getFile().transferTo(chunkFile);

        // 2. 마지막 청크인지 검사
        File[] chunks = tempDirFile.listFiles((dir, name) -> name.startsWith("chunk_"));
        if (chunks != null && chunks.length == dto.getTotalChunks()) {

            // 3. 최종 파일 병합 수행
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

            // 4. 임시 청크 파일 및 폴더 정리
            for (int i = 0; i < dto.getTotalChunks(); i++) {
                File cFile = new File(tempDirFile, "chunk_" + i);
                if (cFile.exists()) {
                    cFile.delete();
                }
            }
            if (tempDirFile.exists()) {
                tempDirFile.delete();
            }
// 5. DB에 파일 메타데이터 저장 (DTO 방식)
            com.kh.back.post.dto.FileMetadataDto fileMetaDataDto = new com.kh.back.post.dto.FileMetadataDto();
            fileMetaDataDto.setOriginalFileName(dto.getOriginalName());
            fileMetaDataDto.setStoredFileName(savedFileName);
            fileMetaDataDto.setFilePath(UPLOAD_DIR + savedFileName);
            fileMetaDataDto.setFileSize(targetFile.length());

            postMapper.saveFileMeta(fileMetaDataDto); // DTO 객체 전달
            return savedFileName; // 마지막 청크 완료 시 저장된 파일명 반환
        }

        return null; // 중간 청크일 때는 null 반환
    }

    /**
     * 게시글 단건 조회 (이미지 리스트 포함)
     */
    @Override
    public PostDto findById(Long postId) {
        PostDto post = postMapper.findById(postId);
        if (post != null) {
            List<PostImageDto> images = postMapper.findImagesByPostId(postId);
            post.setImages(images);
        }
        return post;
    }

    @Transactional
    @Override
    public void save(PostDto postDto, List<MultipartFile> mediaFiles) throws IOException {
        postValidator.validateSave(mediaFiles);
        postMapper.save(postDto);
        Long postId = postDto.getPostId();

        if (mediaFiles != null && !mediaFiles.isEmpty()) {
            int fileOrder = 0;
            for (MultipartFile file : mediaFiles) {
                if (file.isEmpty()) continue;
                SavedFile savedFile = fileUtil.save(file, POST_SUB_DIR, WEB_PREFIX);
                String fileName = new File(savedFile.getPath()).getName();

                PostImageDto imageDto = PostImageDto.builder()
                        .originName(savedFile.getOriginalName())
                        .uploadPath(fileName)
                        .imageOrder(fileOrder++)
                        .build();

                postMapper.saveImage(imageDto);
                postMapper.savePostImage(postId, imageDto.getUploadId());
            }
        }
    }

    @Transactional
    @Override
    public void saveWithFiles(PostDto postDto, List<String> savedFileNames) {
        postMapper.save(postDto);
        Long postId = postDto.getPostId();

        if (savedFileNames != null && !savedFileNames.isEmpty()) {
            int fileOrder = 0;
            for (String savedFileName : savedFileNames) {
                int underscoreIndex = savedFileName.indexOf("_");
                String originName = (underscoreIndex != -1) ? savedFileName.substring(underscoreIndex + 1) : savedFileName;

                PostImageDto imageDto = PostImageDto.builder()
                        .originName(originName)
                        .uploadPath(savedFileName)
                        .imageOrder(fileOrder++)
                        .build();

                postMapper.saveImage(imageDto);
                postMapper.savePostImage(postId, imageDto.getUploadId());
            }
        }
    }

    @Transactional
    @Override
    public void updateWithFiles(PostDto postDto, List<Long> deleteImageIds, List<String> savedFileNames) {
        Long postId = postDto.getPostId();
        postMapper.update(postDto);

        // 1. 삭제할 이미지가 있다면 삭제 처리
        if (deleteImageIds != null && !deleteImageIds.isEmpty()) {
            for (Long uploadId : deleteImageIds) {
                postMapper.deleteByPostIdAndUploadId(postId, uploadId);
                postMapper.deleteImage(uploadId);
            }
        }

        // 2. 새로 추가된 파일명 리스트가 있다면 DB 저장 처리
        if (savedFileNames != null && !savedFileNames.isEmpty()) {
            List<PostImageDto> existingImages = postMapper.findImagesByPostId(postId);
            int imageOrder = existingImages.size();

            for (String savedFileName : savedFileNames) {
                int underscoreIndex = savedFileName.indexOf("_");
                String originName = (underscoreIndex != -1) ? savedFileName.substring(underscoreIndex + 1) : savedFileName;

                PostImageDto imageDto = PostImageDto.builder()
                        .originName(originName)
                        .uploadPath(savedFileName)
                        .imageOrder(imageOrder++)
                        .build();

                postMapper.saveImage(imageDto);
                postMapper.savePostImage(postId, imageDto.getUploadId());
            }
        }
    }

    @Override
    public PageResponseDto<PostDto> getPostPage(PageRequestDto pageRequest, String sort, String keyword) {
        if (pageRequest.getPage() < 1) pageRequest.setPage(1);
        int totalCount = postMapper.countAll(keyword);
        List<PostDto> list = postMapper.findPage(sort, keyword, pageRequest.getOffset(), pageRequest.getSize());

        populateImages(list);
        return new PageResponseDto<>(list, totalCount, pageRequest);
    }

    @Override
    public List<PostDto> findPage(String sort, String keyword, int offset, int size) {
        List<PostDto> list = postMapper.findPage(sort, keyword, offset, size);
        populateImages(list);
        return list;
    }

    @Override
    public List<PostDto> findAll(String sort, String keyword) {
        List<PostDto> list = postMapper.findAll(sort);
        populateImages(list);
        return list;
    }

    // 목록 조회 방식이 달라도 게시글별 이미지는 같은 흐름으로 채운다.
    private void populateImages(List<PostDto> posts) {
        for (PostDto post : posts) {
            post.setImages(postMapper.findImagesByPostId(post.getPostId()));
        }
    }

    @Override
    public void delete(Long postId) {
        deleteById(postId);
    }

    @Transactional
    @Override
    public void deleteById(Long postId) {
        postMapper.deleteById(postId);
    }

    @Override
    public int countAll(String keyword) {
        return postMapper.countAll(keyword);
    }

    @Transactional
    @Override
    public void createPost(String title, String place, String content, Long transportCost, Long foodCost, Long otherCost,
                           String savedFileNamesJson, String savedFileNames) {
        PostDto post = buildPost(title, place, content, transportCost, foodCost, otherCost);
        post.setWriter("익명");
        saveWithFiles(post, parseSavedFileNames(savedFileNamesJson, savedFileNames));
    }

    @Transactional
    @Override
    public void updatePost(Long postId, String title, String place, String content, Long transportCost, Long foodCost,
                           Long otherCost, List<Long> deleteImageIds, String savedFileNamesJson, String savedFileNames) {
        PostDto post = buildPost(title, place, content, transportCost, foodCost, otherCost);
        post.setPostId(postId);
        updateWithFiles(post, deleteImageIds, parseSavedFileNames(savedFileNamesJson, savedFileNames));
    }

    private PostDto buildPost(String title, String place, String content, Long transportCost, Long foodCost, Long otherCost) {
        PostDto post = new PostDto();
        post.setTitle(title);
        post.setPlace(place);
        post.setContent(content);
        post.setTransportCost(transportCost);
        post.setFoodCost(foodCost);
        post.setOtherCost(otherCost);
        return post;
    }

    // 기존 컨트롤러의 파일명 해석 규칙을 업무 서비스로 옮겨 등록과 수정에서 공유한다.
    private List<String> parseSavedFileNames(String savedFileNamesJson, String savedFileNamesAlt) {
        String jsonStr = savedFileNamesJson != null && !savedFileNamesJson.isBlank() ? savedFileNamesJson : savedFileNamesAlt;
        List<String> fileNames = new ArrayList<>();
        if (jsonStr == null || jsonStr.isBlank() || jsonStr.equals("[]")) {
            return fileNames;
        }
        String cleaned = jsonStr.trim();
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        if (!cleaned.isBlank()) {
            for (String part : cleaned.split(",")) {
                String fileName = part.trim().replaceAll("^\"|\"$", "");
                if (!fileName.isEmpty()) {
                    fileNames.add(fileName);
                }
            }
        }
        return fileNames;
    }
}
