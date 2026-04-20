package com.example.pogun.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.kakao.local")
public class KakaoLocalProperties {
    private String restApiKey;
    private String baseUrl = "https://dapi.kakao.com";
}
