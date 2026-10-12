package com.kh.back.auth.security;

import com.kh.back.member.dto.MemberDto;
import com.kh.back.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String memberId) throws UsernameNotFoundException {

        MemberDto memberDto = memberRepository.findByMemberId(memberId);

        if (memberDto == null) {
            throw new UsernameNotFoundException("존재하지 않는 회원입니다.");
        }

        return new CustomUserDetails(memberDto);
    }
}