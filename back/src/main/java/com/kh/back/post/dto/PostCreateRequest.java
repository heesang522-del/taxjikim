package com.post.post.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class PostCreateRequest {
    private String title;
    private String place;
    private String content;
    private Long transportCost;
    private Long foodCost;
    private Long otherCost;
    private List<String> savedFileNames; 
    private List<Long> deleteImageIds;
}
