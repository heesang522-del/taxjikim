package com.post.main.controller;

import com.post.post.service.PostService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final PostService postService;

    public HomeController(PostService postService) {
        this.postService = postService;
    }

    // 루트(/) 또는 /home은 커뮤니티 메인으로 유지
    @GetMapping({"/", "/home"})
    public String home() {
        return "main/index";
    }

    @GetMapping("/terms")
    public String terms() {
        return "legal/terms";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "legal/privacy";
    }
}