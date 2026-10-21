
package com.kh.back.TaxUpload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageOcrResultDto {

    private int pageNumber;  // 페이지숫자

    private String text;    // 텍스트

    private boolean ocrUsed;  // ocr 라이브러리가 사용되는지 여부
}
