package com.kh.back.post.controller;

import com.kh.back.post.dto.PostDto;
import com.kh.back.post.service.PostService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map; // Map 사용을 위해 임포트 추가

@Controller
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    // 메인 피드 / 목록 페이지
    @GetMapping({"/main-post"})
    public String mainPost(
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            Model model
    ) {
        int size = 9; // 페이지당 보여줄 게시글 개수

        // 서비스에서 content(목록)와 paging 정보를 담은 Map 혹은 결과 객체를 리턴한다고 가정할 때
        Map<String, Object> result = postService.getPostPage(page, size, sort, keyword);

        model.addAttribute("posts", result.get("content"));
        model.addAttribute("paging", result.get("paging")); // PageMeta 객체
        model.addAttribute("sort", sort);
        model.addAttribute("keyword", keyword);

        return "post/main-post";
    }

    // 상세 페이지 조회
    @GetMapping("/detail")
    public String detail(@RequestParam(value = "postId", required = false) Long postId, Model model) {
        if (postId == null) {
            return "redirect:/main-post";
        }
        PostDto post = postService.findById(postId);
        model.addAttribute("post", post);
        return "post/post-detail";
    }

    // 수정 페이지 이동 (GET)
    @GetMapping("/edit-post")
    public String editPostForm(@RequestParam("postId") Long postId, Model model) {
        if (postId == null) {
            return "redirect:/main-post";
        }
        PostDto post = postService.findById(postId);

        model.addAttribute("post", post);
        model.addAttribute("postFiles", post.getSavedFileNames()); // 기존 파일 목록 전달

        return "post/edit-post";
    }

    // 수정 내용 제출 처리 (POST) - DB 스키마 반영 및 비용 필드 제거
    @PostMapping("/edit-post")
    public String updatePostProcess(
            @RequestParam("postId") Long postId,
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam(value = "boardType", defaultValue = "INFO") String boardType,
            @RequestParam(value = "jobCategory", required = false) String jobCategory,
            @RequestParam(value = "isPublic", defaultValue = "true") boolean isPublic,
            @RequestParam(value = "deleteImageIds", required = false) List<Long> deleteImageIds,
            @RequestParam(value = "savedFileNames", required = false) List<String> savedFileNames
    ) {
        PostDto postDto = new PostDto();
        postDto.setPostId(postId);
        postDto.setTitle(title);
        postDto.setContent(content);
        postDto.setBoardType(boardType);
        postDto.setJobCategory(jobCategory);
        postDto.setPublic(isPublic);

        // 서비스 호출
        postService.updateWithFiles(postDto, deleteImageIds, savedFileNames);

        return "redirect:/detail?postId=" + postId;
    }

    // 새 게시물 작성 페이지 이동 (GET)
    @GetMapping({"/post/new", "/new-post"})
    public String createPostForm(Model model) {
        model.addAttribute("post", new PostDto());
        return "post/new-post";
    }

    // 삭제 처리
    @PostMapping("/delete-post")
    public String deletePost(@RequestParam("postId") Long postId) {
        if (postId != null) {
            postService.delete(postId);
        }
        return "redirect:/main-post";
    }

    // 새 게시물 등록 처리 (POST) - DB 스키마 반영 및 비용 필드 제거
    @PostMapping("/new-post")
    public String createPostProcess(
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam(value = "boardType", defaultValue = "INFO") String boardType,
            @RequestParam(value = "jobCategory", required = false) String jobCategory,
            @RequestParam(value = "isPublic", defaultValue = "true") boolean isPublic,
            @RequestParam(value = "savedFileNames", required = false) List<String> savedFileNames
    ) {
        System.out.println("=== [DEBUG] 컨트롤러가 받은 savedFileNames: " + savedFileNames);

        PostDto postDto = new PostDto();
        postDto.setTitle(title);
        postDto.setContent(content);
        postDto.setBoardType(boardType);
        postDto.setJobCategory(jobCategory);
        postDto.setPublic(isPublic);

        postService.saveWithFiles(postDto, savedFileNames);

        return "redirect:/main-post";
    }
}
// TODO : CONTROLEER 삭제 FRONT 단에서 처리