package com.ktb.chatapp.dto;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedFileResponse {
    private boolean success;
    private String message;
    private FileResponse file;
    private PresignedUrlResponse presignedUrl;
}
