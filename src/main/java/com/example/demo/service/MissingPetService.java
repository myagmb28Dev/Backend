package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MissingPetService {

    public Map<String, Object> getMissingPetList(String region, String breed, String status, String from, String to, String sort, int page, int size) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("region", region);
        filters.put("breed", breed);
        filters.put("status", status);
        filters.put("from", from);
        filters.put("to", to);
        filters.put("sort", sort);
        filters.put("page", page);
        filters.put("size", size);

        List<Map<String, Object>> data = List.of(
                Map.of("id", "notice-1", "title", "말티즈 실종", "missingRegion", "서울", "status", "OPEN"),
                Map.of("id", "notice-2", "title", "푸들 발견", "missingRegion", "부산", "status", "RESOLVED")
        );
        
        return Map.of(
                "filters", filters,
                "items", data
        );
    }

    public Map<String, Object> createMissingPet(Map<String, Object> request) {
        return Map.of("created", request, "id", "missing-pet-new");
    }

    public Map<String, Object> getMissingPetDetail(String missingPetId) {
        return Map.of(
                "id", missingPetId,
                "title", "말티즈 실종",
                "description", "흰색 목줄 착용",
                "rewardAmount", 300000,
                "contactPhone", "010-1111-2222",
                "images", List.of("https://cdn.ex.com/n1-1.jpg", "https://cdn.ex.com/n1-2.jpg")
        );
    }

    public Map<String, Object> updateMissingPet(String missingPetId, Map<String, Object> request) {
        return Map.of("id", missingPetId, "updated", request);
    }

    public Map<String, Object> changeMissingPetStatus(String missingPetId, String status) {
        return Map.of("id", missingPetId, "status", status);
    }

    public Map<String, Object> increaseMissingPetView(String missingPetId) {
        return Map.of("id", missingPetId, "viewCount", 101);
    }
}
