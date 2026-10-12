package com.kh.back.member.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Getter
@Setter
@ToString(exclude = "memberPassword")
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemberDto {
    private Long id;
    private String memberId;
    private String memberPassword;
    private String memberName;
    private String memberEmail;
    private String memberPhone;
    private String nickname;
    private String profileImage; // DB 저장용 파일 경로 (/uploads/profile/xxx.png)
    private String memberRole;
    private String memberStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    // JSP에서 ${member.formattedCreatedAt} 으로 부를 때 사용되는 메서드
    public String getFormattedCreatedAt() {
        if (this.createdAt == null) return "";
        return this.createdAt.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
    }

    // JSP에서 ${member.formattedUpdatedAt} 으로 부를 때 사용되는 메서드
    public String getFormattedUpdatedAt() {
        if (this.updatedAt == null) return "";
        return this.updatedAt.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
    }

    public String getFormattedDeletedAt() {
        if (this.deletedAt == null) return "";
        return this.deletedAt.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
    }

}
