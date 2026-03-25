package com.example.pogun.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * 애플리케이션 설정을 담당하는 OpenApiConfig이다.
 */

@Configuration
@ConditionalOnClass(OpenAPI.class)
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        final String schemeName = "bearerAuth";

        String subtitle = "반려동물 실종 공고 플랫폼";
        // Swagger 화면에서 모든 보호 API가 Firebase Bearer 토큰을 사용한다는 점을 명시한다.
        return new OpenAPI()
                .info(new Info()
                        .title("Poguen Backend API")
                        .version("v1")
                        .description(subtitle + "\n\n" + "Pogun API Contract based temporary implementation"))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .schemaRequirement(schemeName, new SecurityScheme()
                        .name(schemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("Firebase ID Token"));
    }
}

