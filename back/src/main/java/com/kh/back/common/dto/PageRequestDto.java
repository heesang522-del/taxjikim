package com.kh.back.common.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageRequestDto {
    private int page = 1;
    private int size = 5; // 🌟 기본 사이즈를 5로 변경

    public int getOffset() {
        return (page - 1) * size;
    }
}
