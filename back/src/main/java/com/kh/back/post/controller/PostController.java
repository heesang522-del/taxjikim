package com.post.post.controller;

import com.post.common.response.PageRequest;
import com.post.common.response.PageResponse;
import com.post.common.util.SavedFile;
import com.post.post.dto.PostDto;
import com.post.post.service.PostService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

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
        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(page);
        pageRequest.setSize(9);

        PageResponse pageResponse = postService.getPostPage(pageRequest, sort, keyword);

        model.addAttribute("posts", pageResponse.getList());
        model.addAttribute("paging", pageResponse);
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

    // 🌟 1. 수정 페이지 이동 (GET) - 누락 방지를 위해 필수!
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

    // 🌟 수정 내용 제출 처리 (POST)
    @PostMapping("/edit-post")
    public String updatePostProcess(
            @RequestParam("postId") Long postId,
            @RequestParam("title") String title,
            @RequestParam(value = "place", required = false) String place,
            @RequestParam("content") String content,
            @RequestParam(value = "transportCost", defaultValue = "0") Long transportCost,
            @RequestParam(value = "foodCost", defaultValue = "0") Long foodCost,
            @RequestParam(value = "otherCost", defaultValue = "0") Long otherCost,
            @RequestParam(value = "deleteImageIds", required = false) List<Long> deleteImageIds,
            @RequestParam(value = "savedFileNames", required = false) List<String> savedFileNames // 👈 반드시 String 리스트여야 합니다!
    ) {
        PostDto postDto = new PostDto();
        postDto.setPostId(postId);
        postDto.setTitle(title);
        postDto.setPlace(place);
        postDto.setContent(content);
        postDto.setTransportCost(transportCost);
        postDto.setFoodCost(foodCost);
        postDto.setOtherCost(otherCost);

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

    @PostMapping("/new-post")
    public String createPostProcess(
            @RequestParam("title") String title,
            @RequestParam(value = "place", required = false) String place,
            @RequestParam("content") String content,
            @RequestParam(value = "transportCost", defaultValue = "0") Long transportCost,
            @RequestParam(value = "foodCost", defaultValue = "0") Long foodCost,
            @RequestParam(value = "otherCost", defaultValue = "0") Long otherCost,
            @RequestParam(value = "savedFileNames", required = false) List<String> savedFileNames // 👈 이 이름으로 정확히 매핑됩니다!
    ) {
        System.out.println("=== [DEBUG] 컨트롤러가 받은 savedFileNames: " + savedFileNames);

        PostDto postDto = new PostDto();
        postDto.setTitle(title);
        postDto.setPlace(place);
        postDto.setContent(content);
        postDto.setTransportCost(transportCost);
        postDto.setFoodCost(foodCost);
        postDto.setOtherCost(otherCost);

        postService.saveWithFiles(postDto, savedFileNames);

        return "redirect:/main-post";
    }
}