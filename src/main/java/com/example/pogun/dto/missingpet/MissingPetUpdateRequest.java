package com.example.pogun.dto.missingpet;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * API 요청/응답 데이터 전송 객체인 MissingPetUpdateRequest이다.
 */

@Getter
@Setter
@Schema(description = "실종 공고 수정 요청")
public class MissingPetUpdateRequest {
    @Schema(description = "공고 제목", example = "말티즈를 급히 찾습니다")
    private String title;

    @Schema(description = "동물 종류", example = "DOG")
    private String animalType;

    @Schema(description = "품종", example = "말티즈")
    private String breed;

    @Schema(description = "성별", example = "MALE")
    private String gender;

    @PositiveOrZero
    @Schema(description = "나이", example = "3")
    private Integer age;

    @Schema(description = "색상", example = "WHITE")
    private String color;

    @Schema(description = "상세 설명", example = "흰색 목줄 착용")
    private String description;

    @Schema(description = "실종 시각", example = "2026-03-18T10:00:00Z")
    private String missingDate;

    @Schema(description = "실종 지역", example = "서울 강남구")
    private String missingRegion;

    @Schema(description = "실종 상세 주소", example = "역삼역 인근")
    private String missingAddress;

    @PositiveOrZero
    @Schema(description = "사례금", example = "500000")
    private Integer rewardAmount;

    @Schema(description = "연락처", example = "010-1111-2222")
    private String contactPhone;

    @Schema(description = "공고 상태", example = "OPEN")
    private String status;

    @Schema(description = "이미지 URL 목록")
    private List<String> imageUrls;

    public Map<String, Object> toRequestMap() {
        Map<String, Object> request = new LinkedHashMap<>();
        putIfNotNull(request, "title", title);
        putIfNotNull(request, "animalType", animalType);
        putIfNotNull(request, "breed", breed);
        putIfNotNull(request, "gender", gender);
        putIfNotNull(request, "age", age);
        putIfNotNull(request, "color", color);
        putIfNotNull(request, "description", description);
        putIfNotNull(request, "missingDate", missingDate);
        putIfNotNull(request, "missingRegion", missingRegion);
        putIfNotNull(request, "missingAddress", missingAddress);
        putIfNotNull(request, "rewardAmount", rewardAmount);
        putIfNotNull(request, "contactPhone", contactPhone);
        putIfNotNull(request, "status", status);
        if (imageUrls != null) {
            request.put("imageUrls", imageUrls);
        }
        return request;
    }

    private void putIfNotNull(Map<String, Object> request, String key, Object value) {
        if (value != null) {
            request.put(key, value);
        }
    }
}