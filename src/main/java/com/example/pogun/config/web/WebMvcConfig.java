package com.example.pogun.config.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractJacksonHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JSON UTF-8 응답 정규화와 공용 MVC 설정을 담당하는 WebMvcConfig이다.
 */
@Configuration
public class WebMvcConfig {

    public static final MediaType APPLICATION_JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    OncePerRequestFilter utf8JsonContentTypeFilter() {
        return new Utf8JsonContentTypeFilter();
    }

    @Bean
    ResponseBodyAdvice<Object> utf8JsonResponseAdvice() {
        return new Utf8JsonResponseAdvice();
    }

    public static final class Utf8JsonContentTypeFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, IOException {
            filterChain.doFilter(request, new Utf8JsonHttpServletResponse(response));
        }
    }

    public static final class Utf8JsonResponseAdvice implements ResponseBodyAdvice<Object> {

        @Override
        public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
            return AbstractJacksonHttpMessageConverter.class.isAssignableFrom(converterType);
        }

        @Override
        public Object beforeBodyWrite(
                Object body,
                MethodParameter returnType,
                MediaType selectedContentType,
                Class<? extends HttpMessageConverter<?>> selectedConverterType,
                ServerHttpRequest request,
                ServerHttpResponse response
        ) {
            response.getHeaders().setContentType(APPLICATION_JSON_UTF8);
            return body;
        }
    }

    private static final class Utf8JsonHttpServletResponse extends HttpServletResponseWrapper {

        private Utf8JsonHttpServletResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setContentType(String type) {
            super.setContentType(normalize(type));
        }

        @Override
        public void setHeader(String name, String value) {
            super.setHeader(name, rewrite(name, value));
        }

        @Override
        public void addHeader(String name, String value) {
            super.addHeader(name, rewrite(name, value));
        }

        private String rewrite(String name, String value) {
            if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                return normalize(value);
            }
            return value;
        }

        private String normalize(String value) {
            if (value == null || value.isBlank()) {
                return value;
            }

            MediaType mediaType = MediaType.parseMediaType(value);
            String subtype = mediaType.getSubtype();
            boolean isJson = MediaType.APPLICATION_JSON.includes(mediaType)
                    || (subtype != null && subtype.endsWith("+json"));

            if (!isJson || mediaType.getCharset() != null) {
                return value;
            }

            return new MediaType(mediaType, StandardCharsets.UTF_8).toString();
        }
    }
}
