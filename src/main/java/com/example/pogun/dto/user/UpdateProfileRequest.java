package com.example.pogun.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
/**
 * API 요청/응답 데이터 전송 객체인 UpdateProfileRequest이다.
 */

@Getter
@Setter
@Schema(description = "프로필 수정 요청")
public class UpdateProfileRequest {
    @Size(max = 50, message = "nickname은 50자를 초과할 수 없습니다.")
    @Schema(description = "닉네임", example = "포근한집사")
    private String nickname;

    @Size(max = 30, message = "phoneNumber는 30자를 초과할 수 없습니다.")
    @Schema(description = "전화번호", example = "010-1234-5678")
    private String phoneNumber;

    @Size(max = 1000, message = "profileImageUrl은 1000자를 초과할 수 없습니다.")
    @Schema(description = "프로필 이미지 URL", example = "https://cdn.example.com/profile.png")
    private String profileImageUrl;
}