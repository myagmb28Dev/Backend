package com.example.pogun.service.shelterpet;

import com.example.pogun.dto.shelterpet.ShelterPetDetailResponse;
import com.example.pogun.dto.shelterpet.ShelterPetImageAnalysisDetailResponse;
import com.example.pogun.dto.shelterpet.ShelterPetImageAnalysisResponse;
import com.example.pogun.dto.shelterpet.ShelterPetListFiltersResponse;
import com.example.pogun.dto.shelterpet.ShelterPetListResponse;
import com.example.pogun.dto.shelterpet.ShelterPetStatusResponse;
import com.example.pogun.dto.shelterpet.ShelterPetSummaryResponse;
import com.example.pogun.dto.shelterpet.ShelterPetUpdateResponse;
import com.example.pogun.dto.shelterpet.ShelterPetViewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
/**
 * 도메인 비즈니스 로직을 담당하는 ShelterPetService이다.
 */

@Service
@RequiredArgsConstructor
public class ShelterPetService {

    // 외부 보호소 API 연동 전이라, 프론트가 기대하는 응답 형태를 샘플 데이터로 먼저 유지하고 있다.

    // 목록 응답은 나중에 외부 API 결과를 정규화해도 그대로 재사용할 수 있게 source 와 filter 정보를 함께 담는다.
    public ShelterPetListResponse getShelterPetList(String region, String breed, String status, String sort, int page, int size) {
        List<ShelterPetSummaryResponse> items = List.of(
                new ShelterPetSummaryResponse("ext-1", "서울", "믹스", "OPEN"),
                new ShelterPetSummaryResponse("ext-2", "경기", "푸들", "RESOLVED")
        );

        return new ShelterPetListResponse(
                "KOREA_ANIMAL_PROTECTION_API",
                new ShelterPetListFiltersResponse(region, breed, status, sort, page, size),
                items
        );
    }

    public ShelterPetDetailResponse getShelterPetDetail(String id) {
        return new ShelterPetDetailResponse(
                id,
                "말티즈 실종",
                "흰색 목줄 착용",
                300000,
                "010-1111-2222",
                List.of("https://cdn.ex.com/n1-1.jpg", "https://cdn.ex.com/n1-2.jpg")
        );
    }

    public ShelterPetUpdateResponse updateShelterPet(String id, Map<String, Object> request) {
        return new ShelterPetUpdateResponse(
                id,
                true,
                request.get("title") == null ? null : String.valueOf(request.get("title")),
                request.get("description") == null ? null : String.valueOf(request.get("description")),
                request.get("rewardAmount") instanceof Number number ? number.intValue() : null,
                request.get("contactPhone") == null ? null : String.valueOf(request.get("contactPhone")),
                request.get("imageUrls") instanceof List<?> imageUrls ? imageUrls.stream().map(String::valueOf).toList() : null
        );
    }

    public ShelterPetStatusResponse changeShelterPetStatus(String id, String status) {
        return new ShelterPetStatusResponse(id, status);
    }

    public ShelterPetViewResponse increaseShelterPetView(String id) {
        return new ShelterPetViewResponse(id, 101);
    }

    // 이미지 분석 결과도 현재는 보호소 상세 화면 계약을 위한 placeholder 로 유지된다.
    public ShelterPetImageAnalysisResponse analyzeShelterPetImage(String id) {
        return new ShelterPetImageAnalysisResponse(
                new ShelterPetImageAnalysisDetailResponse(
                        id,
                        "SUCCESS",
                        List.of("흰색", "소형견", "귀가 접힘"),
                        List.of("notice-3", "notice-4")
                )
        );
    }
}