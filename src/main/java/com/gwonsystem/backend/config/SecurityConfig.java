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
                // 1. CORS 설정
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. CSRF 비활성화
                .csrf(csrf -> csrf.disable())

                // 3. API 및 Swagger 접근 권한 허용
                .authorizeHttpRequests(auth -> auth
                        // 기존 /api/** 에 더해 Swagger 전용 주소들을 패스(허용)시킵니다.
                        .requestMatchers(
                                "/api/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()

                        // 그 외의 모든 요청은 여전히 보안 통제
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