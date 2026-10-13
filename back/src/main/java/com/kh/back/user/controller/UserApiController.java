package com.kh.back.user.controller;

import com.kh.back.user.service.UserService;
import com.kh.back.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserApiController {

    private final UserService userService;

    /* ================= 중복 체크 API ================= */

    @GetMapping("/check-id")
    public ApiResponse<Boolean> checkDuplicateId(@RequestParam("loginId") String loginId) {
        return ApiResponse.ok(userService.isIdDuplicate(loginId));
    }

    @GetMapping("/check-nickname")
    public ApiResponse<Boolean> checkDuplicateNickname(@RequestParam("nickname") String nickname) {
        return ApiResponse.ok(userService.isNicknameDuplicate(nickname));
    }

    @GetMapping("/check-email")
    public ApiResponse<Boolean> checkDuplicateEmail(@RequestParam("email") String email) {
        return ApiResponse.ok(userService.isEmailDuplicate(email));
    }
}
