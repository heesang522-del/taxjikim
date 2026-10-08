package com.kh.back.common.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SavedFile {
    private final String originalName; //원본파일명
    private final String saveName;    //저장된 파일명
    private final String path;         //저장된 위치
}