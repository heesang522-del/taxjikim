package com.kh.back.auth.dto;

public record LockedAccountDto(Long userId, String loginId, String userStatus) { }
