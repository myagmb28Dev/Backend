package com.example.pogun.service.user;

import com.example.pogun.service.storage.LocalImageStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * 프로필 이미지를 로컬 파일 시스템에 저장하는 서비스이다.
 */
@Service
public class ProfileImageStorageService {

    private final LocalImageStorageService localImageStorageService;

    public ProfileImageStorageService(LocalImageStorageService localImageStorageService) {
        this.localImageStorageService = localImageStorageService;
    }

    public String storeProfileImage(UUID userId, MultipartFile file) {
        return localImageStorageService.storeImage("profile", "users", userId, file);
    }
}
