package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "프로필 수정 요청")
public class UpdateProfileRequest {
    @Schema(description = "닉네임", example = "포근한집사")
    private String nickname;

    @Schema(description = "전화번호", example = "010-1234-5678")
    private String phoneNumber;

    @Schema(description = "프로필 이미지 URL", example = "https://cdn.example.com/profile.png")
    private String profileImageUrl;
}
