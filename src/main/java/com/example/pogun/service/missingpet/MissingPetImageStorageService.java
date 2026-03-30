package com.example.pogun.service.missingpet;

import com.example.pogun.service.storage.LocalImageStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * 실종 공고 이미지를 로컬 파일 시스템에 저장하는 서비스이다.
 */
@Service
public class MissingPetImageStorageService {

    private final LocalImageStorageService localImageStorageService;

    public MissingPetImageStorageService(LocalImageStorageService localImageStorageService) {
        this.localImageStorageService = localImageStorageService;
    }

    public List<String> storeImages(UUID ownerId, List<MultipartFile> files) {
        return localImageStorageService.storeImages("missing-pets", "notices", ownerId, files);
    }

    public String storeImage(UUID ownerId, MultipartFile file) {
        return localImageStorageService.storeImage("missing-pets", "notices", ownerId, file);
    }
}
