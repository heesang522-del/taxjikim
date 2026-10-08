package com.post.post.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class PostUpdateRequest {
    private Long postId;
    private String title;
    private String content;

    private Long transportCost;
    private Long foodCost;
    private Long otherCost;
    private String place;

    private List<Long> deleteImageIds;
    private List<MultipartFile> imageFiles;

}
