package com.example.pogun.dto.missingpet;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
/**
 * API 요청/응답 데이터 전송 객체인 MissingPetStatusUpdateRequest이다.
 */

@Getter
@Setter
@Schema(description = "실종 공고 상태 변경 요청")
public class MissingPetStatusUpdateRequest {
    @NotBlank
    @Schema(description = "변경할 상태", example = "RESOLVED")
    private String status;
}
