package com.example.pogun.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "온보딩 완료 요청")
public class OnboardingCompleteRequest {
    @NotBlank(message = "region은 필수입니다.")
    @Size(max = 100, message = "region은 100자를 초과할 수 없습니다.")
    @Schema(description = "사용자 활동 지역", example = "서울특별시 강남구")
    private String region;
}
