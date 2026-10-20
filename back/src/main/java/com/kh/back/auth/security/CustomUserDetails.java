package com.kh.back.auth.security;

import com.kh.back.user.dto.UserDto;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CustomUserDetails implements UserDetails {

    // 로그인한 회원 정보를 보관
    private final UserDto userDto;

    public CustomUserDetails(UserDto userDto) {
        this.userDto = userDto;
    }

    // 변경 가능한 프로필 대신 회원 고유 번호로 인증 사용자를 비교한다.
    @EqualsAndHashCode.Include
    public Long getUserId() {
        return userDto.getUserId();
    }

    // 회원 권한 반환
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String role = userDto.getUserRole();
        if (role == null) {
            return List.of(); // 또는 기본 권한 부여
        }

        // "ROLE_" 접두사 자동 처리
        if (!role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }

        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getPassword() {
        return userDto.getPassword();
    }

    @Override
    public String getUsername() {
        return userDto.getLoginId();
    }

    // 계정 만료 여부 (true: 만료 안 됨) - [추가된 필수 메서드]
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    // 계정 잠금 여부 (LOCKED, DORMANT 상태면 잠김 처리 -> LockedException 발생)
    @Override
    public boolean isAccountNonLocked() {
        String status = userDto.getUserStatus();
        return !"LOCKED".equals(status) && !"DORMANT".equals(status);
    }

    // 비밀번호 만료 여부 (true: 만료 안 됨) - [에러 원인 / 추가된 필수 메서드]
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    // 활성화 여부 (BANNED 상태면 비활성화)
    @Override
    public boolean isEnabled() {
        return !"BANNED".equals(userDto.getUserStatus());
    }
}
