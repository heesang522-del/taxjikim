package com.kh.back.auth.controller;

import com.kh.back.auth.repository.MemberSecurityRepository;
import com.kh.back.auth.security.LoginAttemptService;
import com.kh.back.auth.security.LoginUnlockTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUnlockTokenService unlockTokenService;
    private final LoginAttemptService loginAttemptService;
    private final MemberSecurityRepository memberSecurityRepository;

    /* ================= 1. 기본 인증 페이지 ================= */

    // 로그인 페이지
    @GetMapping("/login")
    public String loginForm() {
        return "auth/login";
    }

    // 아이디 찾기 페이지
    @GetMapping("/find-id")
    public String findIdForm() {
        return "auth/find-id";
    }

    // 비밀번호 찾기 페이지
    @GetMapping("/find-password")
    public String findPasswordForm() {
        return "auth/find-password";
    }

    /* ================= 2. 계정 보호 및 복구 페이지 ================= */

    // 이메일 링크를통한 계정 잠금 해제 (AccountUnlockController 통합)
    @GetMapping("/unlock-account")
    public String unlockAccount(@RequestParam("token") String token, RedirectAttributes redirectAttributes) {
        String memberId = unlockTokenService.getMemberIdByToken(token);

        if (memberId == null) {
            String errorMsg = URLEncoder.encode("만료되었거나 유효하지 않은 해제 링크입니다. 다시 시도해 주세요.", StandardCharsets.UTF_8);
            return "redirect:/auth/login?error=true&exception=" + errorMsg;
        }

        memberSecurityRepository.unlockAccount(memberId);
        loginAttemptService.loginSucceeded(memberId);
        unlockTokenService.invalidateToken(token);

        String successMsg = URLEncoder.encode("계정 잠금이 성공적으로 해제되었습니다. 다시 로그인해 주세요.", StandardCharsets.UTF_8);
        return "redirect:/auth/login?error=true&exception=" + successMsg;
    }

    // 휴면 계정 해제 페이지
    @GetMapping("/unlock-dormant")
    public String unlockDormantPage(
            @RequestParam(value = "memberId", required = false) String memberId,
            @RequestParam(value = "memberEmail", required = false) String memberEmail,
            Model model) {

        if (memberId == null || memberEmail == null) {
            return "redirect:/auth/login";
        }

        model.addAttribute("memberId", memberId);
        model.addAttribute("memberEmail", memberEmail);
        return "account/unlock-dormant"; // WEB-INF/views/account/unlock-dormant.jsp
    }

    // 탈퇴 취소/복구 페이지
    @GetMapping("/restore-account")
    public String restoreAccountPage(
            @RequestParam(value = "memberId", required = false) String memberId,
            @RequestParam(value = "memberEmail", required = false) String memberEmail,
            Model model) {

        if (memberId == null || memberEmail == null) {
            return "redirect:/auth/login";
        }

        model.addAttribute("memberId", memberId);
        model.addAttribute("memberEmail", memberEmail);
        return "account/restore-account"; // WEB-INF/views/account/restore.jsp
    }
}