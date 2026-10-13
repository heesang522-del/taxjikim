package com.kh.back.auth.security;

import com.kh.back.user.dto.UserDto;
import com.kh.back.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {

        UserDto userDto = userMapper.findByLoginId(loginId);

        if (userDto == null) {
            throw new UsernameNotFoundException("존재하지 않는 회원입니다.");
        }

        return new CustomUserDetails(userDto);
    }
}
