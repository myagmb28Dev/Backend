package com.example.pogun.dto.admin.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "관리자 Google 로그인 요청")
public class AdminLoginRequest {

    private String firebaseIdToken;
}
