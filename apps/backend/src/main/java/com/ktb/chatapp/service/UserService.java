package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.FileUploadRequest;
import com.ktb.chatapp.dto.PresignedUrlResponse;
import com.ktb.chatapp.dto.ProfileImageResponse;
import com.ktb.chatapp.dto.UpdateProfileRequest;
import com.ktb.chatapp.dto.UserResponse;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.util.ImageValidationUtil;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final FileService fileService;

    @Value("${app.profile.image.max-size:5242880}") // 5MB
    private long maxProfileImageSize;

    /**
     * 현재 사용자 프로필 조회
     * @param email 사용자 이메일
     */
    public UserResponse getCurrentUserProfile(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
        return UserResponse.from(user);
    }

    /**
     * 사용자 프로필 업데이트
     * @param email 사용자 이메일
     */
    public UserResponse updateUserProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        // 프로필 정보 업데이트
        user.setName(request.getName());
        user.setUpdatedAt(LocalDateTime.now());

        User updatedUser = userRepository.save(user);
        log.info("사용자 프로필 업데이트 완료 - ID: {}, Name: {}", user.getId(), request.getName());

        return UserResponse.from(updatedUser);
    }

    /**
     * 프로필 이미지 업로드 (Presigned URL 발급)
     * @param email 사용자 이메일
     */
    public ProfileImageResponse uploadProfileImage(String email, FileUploadRequest request) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        ImageValidationUtil.validateProfileImageMetadata(
                request.getOriginalFilename(),
                request.getContentType(),
                request.getSize(),
                maxProfileImageSize
        );

        if (StringUtils.hasText(user.getProfileImage())) {
            deleteOldProfileImage(user.getProfileImage());
        }

        PresignedUrlResponse presignedUrl = fileService.storeFile(request, "profiles");

        user.setProfileImage(presignedUrl.getObjectKey());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("프로필 이미지 업로드 presigned URL 생성 - User ID: {}, Key: {}", user.getId(), presignedUrl.getObjectKey());

        return ProfileImageResponse.builder()
                .success(true)
                .message("프로필 이미지 업로드 URL이 생성되었습니다.")
                .imageKey(presignedUrl.getObjectKey())
                .presignedUrl(presignedUrl)
                .build();
    }

    /**
     * 특정 사용자 프로필 조회
     */
    public UserResponse getUserProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        return UserResponse.from(user);
    }

    /**
     * 기존 프로필 이미지 삭제
     */
    private void deleteOldProfileImage(String profileImageKey) {
        try {
            if (StringUtils.hasText(profileImageKey)) {
                fileService.deleteFileByPath(profileImageKey);
                log.info("기존 프로필 이미지 삭제 요청 완료: {}", profileImageKey);
            }
        } catch (Exception e) {
            log.warn("기존 프로필 이미지 삭제 실패: {}", e.getMessage());
        }
    }

    /**
     * 프로필 이미지 삭제
     * @param email 사용자 이메일
     */
    public void deleteProfileImage(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            deleteOldProfileImage(user.getProfileImage());
            user.setProfileImage("");
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            log.info("프로필 이미지 삭제 완료 - User ID: {}", user.getId());
        }
    }

    /**
     * 회원 탈퇴 처리
     * @param email 사용자 이메일
     */
    public void deleteUserAccount(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            deleteOldProfileImage(user.getProfileImage());
        }

        userRepository.delete(user);
        log.info("회원 탈퇴 완료 - User ID: {}", user.getId());
    }
}
