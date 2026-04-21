package com.example.pogun.service.location;

import com.example.pogun.config.KakaoLocalProperties;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.location.RegionResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

@Service
public class KakaoLocalService {

    private static final RegionResponse LOCAL_FALLBACK_REGION = new RegionResponse(
            "H",
            "경기도 성남시 분당구 삼평동",
            "경기도",
            "성남시 분당구",
            "삼평동"
    );

    private final KakaoLocalProperties properties;
    private final WebClient webClient;

    public KakaoLocalService(KakaoLocalProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl(properties.getBaseUrl()).build();
    }

    public RegionResponse resolveRegion(double x, double y) {
        if (!StringUtils.hasText(properties.getRestApiKey())) {
            return LOCAL_FALLBACK_REGION;
        }

        try {
            KakaoRegionCodeResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/geo/coord2regioncode.json")
                            .queryParam("x", x)
                            .queryParam("y", y)
                            .queryParam("input_coord", "WGS84")
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.getRestApiKey())
                    .retrieve()
                    .bodyToMono(KakaoRegionCodeResponse.class)
                    .block();

            if (response == null || response.documents() == null || response.documents().isEmpty()) {
                throw ApiException.badRequest("REGION_NOT_FOUND", "좌표에 해당하는 행정구역을 찾을 수 없습니다.");
            }

            KakaoRegionDocument document = response.documents().stream()
                    .filter(item -> "H".equalsIgnoreCase(item.regionType()))
                    .findFirst()
                    .orElse(response.documents().get(0));

            return new RegionResponse(
                    document.regionType(),
                    document.addressName(),
                    document.region1DepthName(),
                    document.region2DepthName(),
                    document.region3DepthName()
            );
        } catch (WebClientResponseException e) {
            throw ApiException.internal("KAKAO_LOCAL_REQUEST_FAILED", "카카오 행정구역 조회 중 오류가 발생했습니다.");
        }
    }

    private record KakaoRegionCodeResponse(
            KakaoRegionMeta meta,
            List<KakaoRegionDocument> documents
    ) {
    }

    private record KakaoRegionMeta(
            Integer totalCount
    ) {
    }

    private record KakaoRegionDocument(
            @com.fasterxml.jackson.annotation.JsonProperty("region_type") String regionType,
            @com.fasterxml.jackson.annotation.JsonProperty("address_name") String addressName,
            @com.fasterxml.jackson.annotation.JsonProperty("region_1depth_name") String region1DepthName,
            @com.fasterxml.jackson.annotation.JsonProperty("region_2depth_name") String region2DepthName,
            @com.fasterxml.jackson.annotation.JsonProperty("region_3depth_name") String region3DepthName
    ) {
    }
}
