package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.FileUploadRequest;
import com.ktb.chatapp.dto.PresignedUrlResponse;
import com.ktb.chatapp.model.File;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.repository.FileRepository;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.util.FileUtil;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@Slf4j
@Service
public class S3FileService implements FileService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final FileRepository fileRepository;
    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final String bucket;
    private final Duration presignDuration;
    private final String baseDirectory;

    public S3FileService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            FileRepository fileRepository,
            MessageRepository messageRepository,
            RoomRepository roomRepository,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${file.upload-dir:uploads}") String uploadDir,
            @Value("${aws.s3.presign-expiration-minutes:15}") long presignMinutes
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.fileRepository = fileRepository;
        this.messageRepository = messageRepository;
        this.roomRepository = roomRepository;
        this.bucket = bucket;
        this.presignDuration = Duration.ofMinutes(presignMinutes);
        this.baseDirectory = sanitizeDirectory(uploadDir);
    }

    @Override
    public FileUploadResult uploadFile(FileUploadRequest request, String uploaderId) {
        try {
            FileUtil.validateFileMetadata(
                    request.getOriginalFilename(),
                    request.getContentType(),
                    request.getSize()
            );

            String safeFileName = FileUtil.generateSafeFileName(request.getOriginalFilename());
            String normalizedOriginalName = FileUtil.normalizeOriginalFilename(request.getOriginalFilename());
            String objectKey = buildObjectKey(null, safeFileName);

            PresignedPutObjectRequest presignedRequest = generatePutObjectRequest(
                    objectKey,
                    request.getContentType(),
                    request.getSize()
            );

            File fileEntity = File.builder()
                    .filename(safeFileName)
                    .originalname(normalizedOriginalName)
                    .mimetype(request.getContentType())
                    .size(request.getSize())
                    .path(objectKey)
                    .user(uploaderId)
                    .uploadDate(LocalDateTime.now())
                    .build();

            File savedFile = fileRepository.save(fileEntity);

            PresignedUrlResponse urlResponse = buildPresignedResponse(
                    presignedRequest,
                    objectKey
            );
            urlResponse.setSuccess(true);

            return FileUploadResult.builder()
                    .success(true)
                    .file(savedFile)
                    .presignedUrl(urlResponse)
                    .build();
        } catch (Exception e) {
            log.error("파일 업로드 presigned URL 생성 실패", e);
            throw new RuntimeException("파일 업로드 URL 생성 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    public PresignedUrlResponse storeFile(FileUploadRequest request, String subDirectory) {
        try {
            FileUtil.validateFileMetadata(
                    request.getOriginalFilename(),
                    request.getContentType(),
                    request.getSize()
            );

            String safeFileName = FileUtil.generateSafeFileName(request.getOriginalFilename());
            String objectKey = buildObjectKey(subDirectory, safeFileName);

            PresignedPutObjectRequest presignedRequest = generatePutObjectRequest(
                    objectKey,
                    request.getContentType(),
                    request.getSize()
            );

            PresignedUrlResponse response = buildPresignedResponse(presignedRequest, objectKey);
            response.setSuccess(true);
            return response;
        } catch (Exception e) {
            log.error("프로필 이미지 presigned URL 생성 실패", e);
            throw new RuntimeException("프로필 이미지 URL 생성 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    public PresignedUrlResponse loadFileAsResource(String fileName, String requesterId) {
        try {
            File fileEntity = fileRepository.findByFilename(fileName)
                    .orElseThrow(() -> new RuntimeException("파일을 찾을 수 없습니다: " + fileName));

            Message message = messageRepository.findByFileId(fileEntity.getId())
                    .orElseThrow(() -> new RuntimeException("파일과 연결된 메시지를 찾을 수 없습니다."));

            Room room = roomRepository.findById(message.getRoomId())
                    .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다."));

            if (!room.getParticipantIds().contains(requesterId)) {
                throw new RuntimeException("파일에 접근할 권한이 없습니다.");
            }

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileEntity.getPath())
                    .responseContentType(fileEntity.getMimetype())
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(builder -> builder
                    .signatureDuration(presignDuration)
                    .getObjectRequest(getObjectRequest));

            PresignedUrlResponse response = buildPresignedResponse(presignedRequest, fileEntity.getPath());
            response.setSuccess(true);
            return response;
        } catch (Exception e) {
            log.error("파일 다운로드 presigned URL 생성 실패: {}", fileName, e);
            throw new RuntimeException("파일 다운로드 URL 생성 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    public boolean deleteFile(String fileId, String requesterId) {
        try {
            File fileEntity = fileRepository.findById(fileId)
                    .orElseThrow(() -> new RuntimeException("파일을 찾을 수 없습니다."));

            if (!fileEntity.getUser().equals(requesterId)) {
                throw new RuntimeException("파일을 삭제할 권한이 없습니다.");
            }

            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileEntity.getPath())
                    .build();

            s3Client.deleteObject(deleteRequest);
            fileRepository.delete(fileEntity);
            return true;
        } catch (Exception e) {
            log.error("파일 삭제 실패: {}", fileId, e);
            throw new RuntimeException("파일 삭제 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    public void deleteFileByPath(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return;
        }

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build();

            s3Client.deleteObject(deleteRequest);
        } catch (Exception e) {
            log.warn("프로필 이미지 삭제 실패: {}", objectKey, e);
        }
    }

    private PresignedPutObjectRequest generatePutObjectRequest(String objectKey, String contentType, long size) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
//                .contentType(contentType)
                .build();

        return s3Presigner.presignPutObject(builder -> builder
                .signatureDuration(presignDuration)
                .putObjectRequest(putObjectRequest));
    }

    private PresignedUrlResponse buildPresignedResponse(PresignedPutObjectRequest request, String objectKey) {
        return PresignedUrlResponse.builder()
                .url(request.url().toString())
                .expiresAt(request.expiration())
                .headers(flattenHeaders(request.httpRequest().headers()))
                .method(request.httpRequest().method().name())
                .objectKey(objectKey)
                .build();
    }

    private PresignedUrlResponse buildPresignedResponse(PresignedGetObjectRequest request, String objectKey) {
        return PresignedUrlResponse.builder()
                .url(request.url().toString())
                .expiresAt(request.expiration())
                .headers(flattenHeaders(request.httpRequest().headers()))
                .method(request.httpRequest().method().name())
                .objectKey(objectKey)
                .build();
    }

    private Map<String, String> flattenHeaders(Map<String, List<String>> headers) {
        Map<String, String> flattened = new HashMap<>();
        headers.forEach((key, value) -> {
            if (value != null && !value.isEmpty()) {
                flattened.put(key, value.get(0));
            }
        });
        return Collections.unmodifiableMap(flattened);
    }

    private String buildObjectKey(String subDirectory, String filename) {
        StringBuilder keyBuilder = new StringBuilder();
        if (StringUtils.hasText(baseDirectory)) {
            keyBuilder.append(baseDirectory);
        }

        if (StringUtils.hasText(subDirectory)) {
            if (!keyBuilder.isEmpty()) {
                keyBuilder.append("/");
            }
            keyBuilder.append(sanitizeDirectory(subDirectory));
        }

        if (!keyBuilder.isEmpty()) {
            keyBuilder.append("/");
        }
        keyBuilder.append(filename);
        return keyBuilder.toString();
    }

    private String sanitizeDirectory(String directory) {
        if (!StringUtils.hasText(directory)) {
            return "";
        }
        String sanitized = directory.replace("\\", "/");
        sanitized = sanitized.replaceAll("^\\./", "");
        if (sanitized.startsWith("/")) {
            sanitized = sanitized.substring(1);
        }
        if (sanitized.endsWith("/")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        return sanitized;
    }
}
