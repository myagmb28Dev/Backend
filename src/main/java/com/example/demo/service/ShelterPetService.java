package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ShelterPetService {

    public Map<String, Object> getShelterPetList(String region, String breed, String status, String sort, int page, int size) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("region", region);
        filters.put("breed", breed);
        filters.put("status", status);
        filters.put("sort", sort);
        filters.put("page", page);
        filters.put("size", size);

        List<Map<String, Object>> items = List.of(
                Map.of("id", "ext-1", "region", "서울", "breed", "믹스", "status", "OPEN"),
                Map.of("id", "ext-2", "region", "경기", "breed", "푸들", "status", "RESOLVED")
        );
        
        return Map.of(
                "source", "KOREA_ANIMAL_PROTECTION_API",
                "filters", filters,
                "items", items
        );
    }

    public Map<String, Object> getShelterPetDetail(String id) {
        return Map.of(
                "id", id,
                "title", "말티즈 실종",
                "description", "흰색 목줄 착용",
                "rewardAmount", 300000,
                "contactPhone", "010-1111-2222",
                "images", List.of("https://cdn.ex.com/n1-1.jpg", "https://cdn.ex.com/n1-2.jpg")
        );
    }

    public Map<String, Object> updateShelterPet(String id, Map<String, Object> request) {
        return Map.of("id", id, "updated", request);
    }

    public Map<String, Object> changeShelterPetStatus(String id, String status) {
        return Map.of("id", id, "status", status);
    }

    public Map<String, Object> increaseShelterPetView(String id) {
        return Map.of("id", id, "viewCount", 101);
    }

    public Map<String, Object> analyzeShelterPetImage(String id) {
        return Map.of(
                "analysis", Map.of(
                        "noticeId", id,
                        "analysisStatus", "SUCCESS",
                        "features", List.of("흰색", "소형견", "귀가 접힘"),
                        "similarNotices", List.of("notice-3", "notice-4")
                )
        );
    }
}
