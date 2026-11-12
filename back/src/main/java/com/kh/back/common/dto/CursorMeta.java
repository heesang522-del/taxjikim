package com.kh.back.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 커서 기반 목록 응답의 페이징 정보.
 * 번호 페이징(PageMeta)과 달리 다음 조회에 그대로 넘길 커서 값만 전달한다.
 */
@Getter
@AllArgsConstructor
public class CursorMeta {

    private final boolean hasMore;
    private final Long nextCursor; // 다음 조회에 쓸 커서 값. 더 없으면 null
}
