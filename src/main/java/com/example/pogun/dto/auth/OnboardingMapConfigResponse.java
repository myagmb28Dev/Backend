package com.example.pogun.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "온보딩 지도 설정 응답")
public record OnboardingMapConfigResponse(
        @Schema(description = "카카오 로컬 REST API 키 설정 여부") boolean kakaoRestKeyConfigured,
        @Schema(description = "카카오 지도 JavaScript 키 설정 여부") boolean kakaoJavascriptKeyConfigured,
        @Schema(description = "카카오 지도 JavaScript 키. 브라우저 SDK 로딩용 공개 키") String kakaoJavascriptKey
) {
}
