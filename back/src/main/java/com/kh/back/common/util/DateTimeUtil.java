package com.kh.back.common.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class DateTimeUtil {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private DateTimeUtil() {
    }

    // DB의 날짜·시각을 응답할 때 한국 시간대가 포함된 ISO 문자열로 표현한다.
    public static String toSeoulString(LocalDateTime value) {
        return value == null ? null : value.atZone(SEOUL).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
