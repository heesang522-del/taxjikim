package com.kh.back.user.controller;

import com.kh.back.user.service.UserService;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.user.dto.UserDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MultipartFile;
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

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Long>> createUser(@RequestBody UserDto request) {
        userService.joinUser(request, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(request.getUserId()));
    }

    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ResponseEntity<ApiResponse<Long>> createUserWithProfile(@ModelAttribute UserDto request,
            @RequestParam(value = "profile", required = false) MultipartFile profile) {
        // 기존 회원가입 폼의 선택 프로필 업로드도 같은 업무 흐름을 사용한다.
        userService.joinUser(request, profile);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(request.getUserId()));
    }

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
