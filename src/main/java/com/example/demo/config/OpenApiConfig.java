package com.example.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(OpenAPI.class)
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        final String schemeName = "bearerAuth";

        String subtitle = "반려동물 실종 공고 플랫폼";
        return new OpenAPI()
                .info(new Info()
                        .title("Pogun Backend API")
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

