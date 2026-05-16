package com.example.pogun.config.web;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.kakao.local")
public class KakaoLocalProperties {
    private String restApiKey;
    private String javascriptKey;
    private String baseUrl = "https://dapi.kakao.com";
}
