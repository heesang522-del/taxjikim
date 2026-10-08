package com.kh.back.common.response;

import lombok.Getter;
import java.util.List;

@Getter
public class PageResponse<T> {

    private final List<T> content;     // 조회된 게시글 목록 데이터
    private final int totalCount;      // 전체 게시글 개수
    private final int page;            // 현재 페이지 번호
    private final int size;            // 페이지당 개수 (5개)
    private final int totalPages;      // 전체 페이지 수
    private final boolean hasNext;     // 다음 페이지 존재 여부

    public PageResponse(List<T> content, int totalCount, PageRequest pageRequest) {
        this.content = content;
        this.totalCount = totalCount;
        this.page = pageRequest.getPage();
        this.size = pageRequest.getSize();
        this.totalPages = (int) Math.ceil((double) totalCount / size);
        this.hasNext = this.page < this.totalPages;
    }
}