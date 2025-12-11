package com.ktb.chatapp.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ImageValidationUtil {

    public void validateProfileImageMetadata(
            String originalFilename,
            String contentType,
            long size,
            long maxSize
    ) {
        FileUtil.validateFileMetadata(originalFilename, contentType, size);

        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("이미지 파일만 업로드할 수 있습니다.");
        }

        if (size > maxSize) {
            throw new RuntimeException("이미지 파일은 " + (maxSize / 1024 / 1024) + "MB를 초과할 수 없습니다.");
        }
    }
}
