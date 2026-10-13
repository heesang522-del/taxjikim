package com.kh.back.user.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.auth.security.LoginSessionRepository;
import com.kh.back.user.dto.UserDto;
import com.kh.back.user.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;


@Controller
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final LoginSessionRepository loginSessionRepository;

    /* ================= 1. 페이지 이동 (GET) ================= */

    @GetMapping("/join-step1")
    public String joinStep1() {
        return "user/join-step1";
    }

    @GetMapping("/join-step2")
    public String joinStep2() {
        return "user/join-step2";
    }

    @GetMapping("/mypage")
    public String mypageForm(@AuthenticationPrincipal CustomUserDetails user, @RequestParam(defaultValue = "1") Integer page,
            Model model) {
        UserDto userDto = user.getUserDto();
        Long pkId = (userDto.getUserId() != null) ? userDto.getUserId() : 0;

        return "mypage/main"; // WEB-INF/views/mypage/main.jsp
    }

    @GetMapping("/settings")
    public String settingsForm(@AuthenticationPrincipal CustomUserDetails user, Model model) {
        UserDto userDto = userService.getUserByLoginId(user.getUsername());
        model.addAttribute("userDto", userDto);

        return "mypage/settings"; // WEB-INF/views/mypage/settings.jsp
    }

    /* ================= 2. 정보 처리 및 변경 (POST) ================= */

    @PostMapping("/join")
    public String join(UserDto userDto, @RequestParam(value = "profile", required = false) MultipartFile profileFile) {
        userService.joinUser(userDto, profileFile);
        return "redirect:/auth/login";
    }

    @PostMapping("/updateNickname")
    public String updateNickname(String nickname, Principal principal) {
        // DB 업데이트만 수행 (세션 저장 로직 전면 제거)
        userService.updateNickname(principal.getName(), nickname);
        return "redirect:/user/settings";
    }


    @PostMapping("/updateEmail")
    public String updateEmail(@RequestParam("email") String email, @AuthenticationPrincipal CustomUserDetails userDetails) {
        userService.updateEmail(userDetails.getUsername(), email);
        return "redirect:/user/settings";
    }

    @PostMapping("/updateProfileImageUrl")
    public String updateProfileImageUrl(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "deleteProfile", defaultValue = "false") boolean deleteProfile,
            @RequestParam(value = "profile", required = false) MultipartFile profileFile,
            RedirectAttributes redirectAttributes) {

        userService.updateProfileImageUrl(userDetails.getUserDto().getLoginId(), profileFile, deleteProfile);
        redirectAttributes.addFlashAttribute("message", "프로필 이미지가 변경되었습니다.");
        return "redirect:/user/settings";
    }

    @PostMapping("/updatePasswordInSettings")
    public String updatePasswordInSettings(
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes rttr) {

        try {
            userService.updatePasswordInSettings(userDetails.getUsername(), currentPassword, newPassword);
            rttr.addFlashAttribute("successMessage", "비밀번호가 변경되었습니다.");
        } catch (IllegalArgumentException e) {
            rttr.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/user/settings";
    }

    @PostMapping("/delete")
    public String deleteUser(
            @RequestParam("password") String password,
            HttpServletRequest request,
            HttpServletResponse response,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes rttr) {

        if (userDetails == null) {
            return "redirect:/auth/login";
        }

        String loginId = userDetails.getUsername();
        boolean isPasswordMatch = userService.checkPassword(loginId, password);

        if (!isPasswordMatch) {
            rttr.addFlashAttribute("errorMessage", "비밀번호가 일치하지 않습니다.");
            return "redirect:/user/settings";
        }

        try {
            userService.deleteUser(loginId);

            // 1. Redis에서 Refresh Token 삭제
            loginSessionRepository.revokeCurrentSession(userDetails.getUserDto().getUserId());

            // 2. 브라우저의 refreshToken 쿠키 만료 처리
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("refreshToken".equals(cookie.getName())) {
                        Cookie deleteCookie = new Cookie("refreshToken", null);
                        deleteCookie.setPath("/");
                        deleteCookie.setMaxAge(0);
                        response.addCookie(deleteCookie);
                        break;
                    }
                }
            }

            // 3. 메모리 상의 SecurityContext 인증 정보만 초기화 (세션 로그아웃 핸들러 대체)
            SecurityContextHolder.clearContext();

            rttr.addFlashAttribute("successMessage", "계정 삭제 신청이 완료되었습니다.");
            return "redirect:/";

        } catch (Exception e) {
            e.printStackTrace();
            rttr.addFlashAttribute("errorMessage", "탈퇴 처리 중 오류가 발생했습니다.");
            return "redirect:/user/settings";
        }
    }
}