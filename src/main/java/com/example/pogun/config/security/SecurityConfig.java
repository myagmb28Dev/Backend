package com.example.pogun.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;
/**
 * 애플리케이션 설정을 담당하는 SecurityConfig이다.
 */

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final FirebaseTokenFilter firebaseTokenFilter;
    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;
    private final ApiAccessDeniedHandler apiAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // JWT/Firebase 기반 인증만 사용하므로 세션 없이 401/403 JSON 응답을 일관되게 유지한다.
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(apiAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/dm-flow.html")
                        .permitAll()
                        .requestMatchers("/notice-flow.html")
                        .permitAll()
                        .requestMatchers("/shelter-flow.html")
                        .permitAll()
                        .requestMatchers("/login-flow.html")
                        .permitAll()
                        .requestMatchers("/notification-flow.html")
                        .permitAll()
                        .requestMatchers("/firebase-web-config.js")
                        .permitAll()
                        .requestMatchers("/notification-web.js")
                        .permitAll()
                        .requestMatchers("/notification-push.js")
                        .permitAll()
                        .requestMatchers("/firebase-messaging-sw.js")
                        .permitAll()
                        .requestMatchers(
                                "/",
                                "/health",
                                "/favicon.ico",
                                "/error",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/onboarding/map-config").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/missing-pets/ai-source").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/missing-pets/*/ai-source").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/shelter/ai-source").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/shelter/*/ai-source").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/missing-pets/*/analysis-result").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/shelter/*/analysis-result").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/ws/chat").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(firebaseTokenFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "http://127.0.0.1:3000",
                "http://localhost:8080",
                "http://127.0.0.1:8080",
                "http://43.201.1.61",
                "http://43.201.1.61:8080",
                "http://paw.gbsw.hs.kr",
                "https://paw.gbsw.hs.kr"
        ));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}




