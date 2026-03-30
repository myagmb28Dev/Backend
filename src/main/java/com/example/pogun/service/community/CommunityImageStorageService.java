package com.example.pogun.service.community;

import com.example.pogun.service.storage.LocalImageStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * 커뮤니티 게시글 이미지를 로컬 파일 시스템에 저장하는 서비스이다.
 */
@Service
public class CommunityImageStorageService {

    private final LocalImageStorageService localImageStorageService;

    public CommunityImageStorageService(LocalImageStorageService localImageStorageService) {
        this.localImageStorageService = localImageStorageService;
    }

    public List<String> storeImages(UUID ownerId, List<MultipartFile> files) {
        return localImageStorageService.storeImages("community", "posts", ownerId, files);
    }

    public String storeImage(UUID ownerId, MultipartFile file) {
        return localImageStorageService.storeImage("community", "posts", ownerId, file);
    }
}
