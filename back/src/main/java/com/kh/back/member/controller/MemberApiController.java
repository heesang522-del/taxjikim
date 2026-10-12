package com.kh.back.member.controller;

import com.kh.back.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberApiController {

    private final MemberService memberService;

    /* ================= 중복 체크 API ================= */

    @GetMapping("/check-id")
    public boolean checkDuplicateId(@RequestParam("memberId") String memberId) {
        return memberService.isIdDuplicate(memberId);
    }

    @GetMapping("/check-nickname")
    public boolean checkDuplicateNickname(@RequestParam("nickname") String nickname) {
        return memberService.isNicknameDuplicate(nickname);
    }

    @GetMapping("/check-email")
    public boolean checkDuplicateEmail(@RequestParam("memberEmail") String memberEmail) {
        return memberService.isEmailDuplicate(memberEmail);
    }
}