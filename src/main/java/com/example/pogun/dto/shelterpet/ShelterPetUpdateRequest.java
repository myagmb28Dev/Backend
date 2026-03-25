package com.example.pogun.dto.shelterpet;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * API 요청/응답 데이터 전송 객체인 ShelterPetUpdateRequest이다.
 */

@Getter
@Setter
@Schema(description = "외부 공고 수정 요청")
public class ShelterPetUpdateRequest {
    @Schema(description = "공고 제목", example = "말티즈 실종")
    private String title;

    @Schema(description = "상세 설명", example = "흰색 목줄 착용")
    private String description;

    @PositiveOrZero
    @Schema(description = "사례금", example = "300000")
    private Integer rewardAmount;

    @Schema(description = "연락처", example = "010-1111-2222")
    private String contactPhone;

    @Schema(description = "이미지 URL 목록")
    private List<String> imageUrls;

    public Map<String, Object> toRequestMap() {
        Map<String, Object> request = new LinkedHashMap<>();
        putIfNotNull(request, "title", title);
        putIfNotNull(request, "description", description);
        putIfNotNull(request, "rewardAmount", rewardAmount);
        putIfNotNull(request, "contactPhone", contactPhone);
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