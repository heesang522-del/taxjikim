package com.kh.back.user.dto;

// 로그인 상태 복원과 회원정보 화면에 필요한 값만 외부에 전달한다.
public record MyProfileResponseDto(
        Long userId,
        String loginId,
        String email,
        String nickname,
        String profileImageUrl,
        String userRole
) {
    public static MyProfileResponseDto from(UserDto user) {
        return new MyProfileResponseDto(
                user.getUserId(),
                user.getLoginId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getUserRole()
        );
    }
}
