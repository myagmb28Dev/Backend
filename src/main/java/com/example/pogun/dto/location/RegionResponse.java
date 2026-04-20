package com.example.pogun.dto.location;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "행정구역 정보")
public record RegionResponse(
        @Schema(description = "H(행정동) 또는 B(법정동)") String regionType,
        @Schema(description = "전체 지역 명칭") String addressName,
        @Schema(description = "지역 1Depth, 시도 단위") String region1DepthName,
        @Schema(description = "지역 2Depth, 구 단위") String region2DepthName,
        @Schema(description = "지역 3Depth, 동 단위") String region3DepthName
) {
}
