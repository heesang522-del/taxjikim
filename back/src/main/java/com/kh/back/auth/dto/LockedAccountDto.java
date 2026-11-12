package com.kh.back.auth.dto;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.kh.back.common.util.DateTimeUtil;
import java.time.LocalDateTime;

public record LockedAccountDto(Long userId, String loginId, String userStatus, LocalDateTime updatedAt) {
    // 다른 회원 응답과 동일하게 한국 시간대가 포함된 시각으로 전달한다.
    @JsonGetter("updatedAt")
    public String getUpdatedAtJson() {
        return DateTimeUtil.toSeoulString(updatedAt);
    }
}
