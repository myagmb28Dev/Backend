package com.example.pogun.config;

import org.springframework.context.annotation.Configuration;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 정적 리소스와 로컬 업로드 파일 서빙을 담당하는 WebMvcConfig이다.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final NoticeChatUploadAccessInterceptor noticeChatUploadAccessInterceptor;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(noticeChatUploadAccessInterceptor)
                .addPathPatterns("/uploads/notice-chat/**");
    }
}
