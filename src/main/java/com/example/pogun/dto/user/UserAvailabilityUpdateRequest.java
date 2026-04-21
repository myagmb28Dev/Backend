package com.example.pogun.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "사용자 가용 상태 변경 요청")
public class UserAvailabilityUpdateRequest {
    @NotBlank(message = "availabilityStatus는 필수입니다.")
    @Schema(description = "가용 상태", example = "IDLE")
    private String availabilityStatus;
}
