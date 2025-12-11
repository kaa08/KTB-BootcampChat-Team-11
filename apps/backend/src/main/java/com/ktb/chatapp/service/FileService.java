package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.FileUploadRequest;
import com.ktb.chatapp.dto.PresignedUrlResponse;

public interface FileService {

    FileUploadResult uploadFile(FileUploadRequest request, String uploaderId);

    PresignedUrlResponse storeFile(FileUploadRequest request, String subDirectory);

    PresignedUrlResponse loadFileAsResource(String fileName, String requesterId);

    boolean deleteFile(String fileId, String requesterId);

    void deleteFileByPath(String objectKey);
}
