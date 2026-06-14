package com.gwonsystem.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. CORS 설정을 직접 커스텀한 소스(아래 bean)로 지정합니다.
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. REST API 환경이므로 CSRF 보안을 비활성화합니다. (안 끄면 POST, PUT 요청 시 403 유발)
                .csrf(csrf -> csrf.disable())

                // 3. API 접근 권한 설정
                .authorizeHttpRequests(auth -> auth
                        // 프론트에서 호출하는 /api/ 로 시작하는 모든 주소는 로그인 없이 허용합니다.
                        .requestMatchers("/api/**").permitAll()
                        // 그 외의 모든 요청은 인증(로그인)을 받아야 합니다.
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    // 💡 핵심: Vercel 주소 및 모든 메서드를 허용하는 CORS 설정입니다.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 특정 주소만 허용하고 싶다면 아래와 같이 작성하고, 테스트 단계라면 "*"를 쓰셔도 됩니다.
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true); // 쿠키나 인증 헤더를 허용할 때 필수 설정

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // 모든 API 경로에 적용
        return source;
    }
}