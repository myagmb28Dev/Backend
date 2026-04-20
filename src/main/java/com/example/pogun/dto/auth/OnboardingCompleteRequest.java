package com.example.pogun.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "온보딩 완료 요청")
public class OnboardingCompleteRequest {
    @NotNull(message = "x는 필수입니다.")
    @DecimalMin(value = "124.0", message = "x는 대한민국 경도 범위여야 합니다.")
    @DecimalMax(value = "132.0", message = "x는 대한민국 경도 범위여야 합니다.")
    @Schema(description = "X 좌표값, WGS84 경도(longitude)", example = "127.1086228")
    private Double x;

    @NotNull(message = "y는 필수입니다.")
    @DecimalMin(value = "33.0", message = "y는 대한민국 위도 범위여야 합니다.")
    @DecimalMax(value = "39.5", message = "y는 대한민국 위도 범위여야 합니다.")
    @Schema(description = "Y 좌표값, WGS84 위도(latitude)", example = "37.4012191")
    private Double y;
}
