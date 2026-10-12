package com.kh.back.member.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.auth.security.LoginSessionRepository;
import com.kh.back.member.dto.MemberDto;
import com.kh.back.member.service.MemberService;
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
@RequestMapping("/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final LoginSessionRepository loginSessionRepository;

    /* ================= 1. 페이지 이동 (GET) ================= */

    @GetMapping("/join-step1")
    public String joinStep1() {
        return "member/join-step1";
    }

    @GetMapping("/join-step2")
    public String joinStep2() {
        return "member/join-step2";
    }

    @GetMapping("/mypage")
    public String mypageForm(@AuthenticationPrincipal CustomUserDetails user, @RequestParam(defaultValue = "1") Integer page,
            Model model) {
        MemberDto memberDto = user.getMemberDto();
        Long pkId = (memberDto.getId() != null) ? memberDto.getId() : 0;

        return "mypage/main"; // WEB-INF/views/mypage/main.jsp
    }

    @GetMapping("/settings")
    public String settingsForm(@AuthenticationPrincipal CustomUserDetails user, Model model) {
        MemberDto memberDto = memberService.getMemberById(user.getUsername());
        model.addAttribute("memberDto", memberDto);

        return "mypage/settings"; // WEB-INF/views/mypage/settings.jsp
    }

    /* ================= 2. 정보 처리 및 변경 (POST) ================= */

    @PostMapping("/join")
    public String join(MemberDto memberDto, @RequestParam(value = "profile", required = false) MultipartFile profileFile) {
        memberService.joinMember(memberDto, profileFile);
        return "redirect:/auth/login";
    }

    @PostMapping("/updateNickname")
    public String updateNickname(String nickname, Principal principal) {
        // DB 업데이트만 수행 (세션 저장 로직 전면 제거)
        memberService.updateNickname(principal.getName(), nickname);
        return "redirect:/member/settings";
    }

    @PostMapping("/updatePhone")
    public String updatePhone(@RequestParam("memberPhone") String memberPhone, @AuthenticationPrincipal CustomUserDetails userDetails) {
        memberService.updatePhone(userDetails.getUsername(), memberPhone);
        return "redirect:/member/settings";
    }

    @PostMapping("/updateEmail")
    public String updateEmail(@RequestParam("memberEmail") String memberEmail, @AuthenticationPrincipal CustomUserDetails userDetails) {
        memberService.updateEmail(userDetails.getUsername(), memberEmail);
        return "redirect:/member/settings";
    }

    @PostMapping("/updateProfileImage")
    public String updateProfileImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "deleteProfile", defaultValue = "false") boolean deleteProfile,
            @RequestParam(value = "profile", required = false) MultipartFile profileFile,
            RedirectAttributes redirectAttributes) {

        memberService.updateProfileImage(userDetails.getMemberDto().getMemberId(), profileFile, deleteProfile);
        redirectAttributes.addFlashAttribute("message", "프로필 이미지가 변경되었습니다.");
        return "redirect:/member/settings";
    }

    @PostMapping("/updatePasswordInSettings")
    public String updatePasswordInSettings(
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes rttr) {

        try {
            memberService.updatePasswordInSettings(userDetails.getUsername(), currentPassword, newPassword);
            rttr.addFlashAttribute("successMessage", "비밀번호가 변경되었습니다.");
        } catch (IllegalArgumentException e) {
            rttr.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/member/settings";
    }

    @PostMapping("/delete")
    public String deleteMember(
            @RequestParam("password") String password,
            HttpServletRequest request,
            HttpServletResponse response,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes rttr) {

        if (userDetails == null) {
            return "redirect:/auth/login";
        }

        String memberId = userDetails.getUsername();
        boolean isPasswordMatch = memberService.checkPassword(memberId, password);

        if (!isPasswordMatch) {
            rttr.addFlashAttribute("errorMessage", "비밀번호가 일치하지 않습니다.");
            return "redirect:/member/settings";
        }

        try {
            memberService.deleteMember(memberId);

            // 1. Redis에서 Refresh Token 삭제
            loginSessionRepository.revokeCurrentSession(userDetails.getMemberDto().getId());

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
            return "redirect:/member/settings";
        }
    }
}