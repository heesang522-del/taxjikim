package com.kh.back.chatbot.util;

import java.util.regex.Pattern;

public final class PiiMasker {

    private static final Pattern RRN   = Pattern.compile("\\d{6}\\s?-?\\s?[1-4]\\d{6}");              // 주민등록번호
    private static final Pattern CARD  = Pattern.compile("\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}");  // 카드번호
    private static final Pattern PHONE = Pattern.compile("01[016789][- ]?\\d{3,4}[- ]?\\d{4}");       // 휴대전화
    private static final Pattern ACCOUNT = Pattern.compile("\\d{2,6}-\\d{2,6}-\\d{4,8}");            // 계좌번호 (마지막 4자리 이상 → 날짜 2026-10-27 은 걸리지 않는다)
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");

    private PiiMasker() {}

    public static String mask(String text) {
        String result = RRN.matcher(text).replaceAll("[주민번호]");
        result = CARD.matcher(result).replaceAll("[카드번호]");
        result = PHONE.matcher(result).replaceAll("[전화번호]");
        result = ACCOUNT.matcher(result).replaceAll("[계좌번호]");
        result = EMAIL.matcher(result).replaceAll("[이메일]");
        return result;
    }
}