package com.kh.back.taxupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageOcrResultDto {

    private int pageNumber;

    private String text;
    private boolean ocrUsed;

    private List<OcrWord> words;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OcrWord {

        private String text;

        // Python PDF 좌표와 같은 포인트 단위로 전달한다.
        private double x0;
        private double x1;
        private double top;
    }
}