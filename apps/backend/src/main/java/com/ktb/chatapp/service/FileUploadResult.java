package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.PresignedUrlResponse;
import com.ktb.chatapp.model.File;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Getter
@Builder
public class FileUploadResult {
    private boolean success;
    private File file;
    private PresignedUrlResponse presignedUrl;
}
