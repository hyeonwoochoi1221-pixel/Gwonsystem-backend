package com.gwonsystem.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CORS 및 CSRF 설정 해제 (프론트엔드 연동을 위해 보통 해제하거나 따로 설정함)
                .cors(cors -> cors.disable())
                .csrf(csrf -> csrf.disable())

                // 모든 요청에 대해 로그인 없이 접근 허용
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )

                // 기본 formLogin 및 httpBasic 로그인 화면 비활성화
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        return http.build();
    }
}