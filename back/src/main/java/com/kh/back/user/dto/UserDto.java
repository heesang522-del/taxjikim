package com.kh.back.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.kh.back.common.util.DateTimeUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
@ToString(exclude = "password")
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDto {
    private Long userId;
    private String loginId;
    // 요청에서는 비밀번호를 받지만 응답 JSON에는 해시를 포함하지 않는다.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String name;
    private String email;
    // 생년월일은 선택 입력이며 날짜만 받는다.
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate birthDate;
    private String nickname;
    private String profileImageUrl; // DB 저장용 파일 경로 (/uploads/profile/xxx.png)
    private String userRole;
    private String userStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private LocalDateTime lastLoginAt;

    @JsonGetter("createdAt")
    public String getCreatedAtJson() {
        return DateTimeUtil.toSeoulString(createdAt);
    }

    @JsonGetter("updatedAt")
    public String getUpdatedAtJson() {
        return DateTimeUtil.toSeoulString(updatedAt);
    }

    @JsonGetter("deletedAt")
    public String getDeletedAtJson() {
        return DateTimeUtil.toSeoulString(deletedAt);
    }

    @JsonGetter("lastLoginAt")
    public String getLastLoginAtJson() {
        return DateTimeUtil.toSeoulString(lastLoginAt);
    }

}
