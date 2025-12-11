package com.ktb.chatapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProfileImageResponse {
    private boolean success;
    private String message;
    private String imageKey;
    private PresignedUrlResponse presignedUrl;
}
