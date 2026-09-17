// src/main/java/com/gwonsystem/backend/config/SecurityConfig.java
package com.gwonsystem.backend.config;

import com.gwonsystem.backend.security.OAuth2SuccessHandler;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    @Bean
    public RoleHierarchy roleHierarchy() {
        RoleHierarchyImpl hierarchy = new RoleHierarchyImpl();
        hierarchy.setHierarchy(
                "ROLE_SUPERVISOR > ROLE_REGULAR\n" +
                        "ROLE_REGULAR > ROLE_ASSOCIATE"
        );
        return hierarchy;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. CORS 및 기본 보안 비활성화
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // 2. 미인증 요청 시 302 리다이렉트 방지 및 순수 401 JSON 응답
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.getWriter().write("{\"message\":\"로그인이 필요합니다.\"}");
                        })
                )

                .authorizeHttpRequests(auth -> auth
                        // 🌟 [우선순위 1] 브라우저 CORS 사전 검사 (Preflight OPTIONS) 전면 허용
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 🌟 [우선순위 2] 공지사항 조회 (목록 / 단건 전체) 전면 허용
                        .requestMatchers(HttpMethod.GET, "/api/notices", "/api/notices/**").permitAll()

                        // 🌟 [우선순위 3] 관리자(슈퍼바이저) 전용 경로를 일반 회원 허용 규칙보다 먼저 검사
                        .requestMatchers(
                                "/api/members/admin/**",
                                "/api/members/*/role",
                                "/api/members/*/org-info"
                        ).hasRole("SUPERVISOR")

                        // 🌟 [우선순위 4] 회원가입, 로그인, 중복체크, 이메일OTP, 비밀번호검증, 프로필 수정 등 일반 회원 API 전면 허용
                        .requestMatchers(
                                "/api/members/register",
                                "/api/members/login",
                                "/api/members/check-username",
                                "/api/members/email/**",
                                "/api/members/find-username",
                                "/api/members/password/**",
                                "/api/members/verify-password",
                                "/api/members/profile",
                                "/api/members/**"
                        ).permitAll()

                        // 공지사항 등록/수정/삭제 권한 통제
                        .requestMatchers(HttpMethod.POST, "/api/notices", "/api/notices/**").hasAnyRole("REGULAR", "SUPERVISOR")
                        .requestMatchers(HttpMethod.PUT, "/api/notices", "/api/notices/**").hasAnyRole("REGULAR", "SUPERVISOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/notices", "/api/notices/**").hasAnyRole("REGULAR", "SUPERVISOR")

                        // 스프링 기본 에러 경로 및 파비콘
                        .requestMatchers("/error", "/favicon.ico").permitAll()

                        // 소셜 로그인 엔드포인트 전체 허용
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/login/**"
                        ).permitAll()

                        // Swagger 및 OpenAPI UI
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()

                        // 일반 조회용 GET API 전체 허용
                        .requestMatchers(HttpMethod.GET, "/api/**").permitAll()

                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )

                // 소셜 로그인 핸들러 연동
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2SuccessHandler)
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 🌟 운영 도메인(gwonsystem.com, www.gwonsystem.com) 및 배포 도메인 전체 등록
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "https://*.vercel.app",
                "https://gwonsystem-backend.onrender.com",
                "https://www.gwonsystem.com",
                "https://gwonsystem.com",
                "https://*.gwonsystem.com"
        ));

        // 모든 HTTP 메서드 허용
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // 모든 요청 헤더 허용 (CORS Preflight 차단 방지)
        configuration.setAllowedHeaders(List.of("*"));

        // 인증 자격 증명(쿠키, 헤더) 허용
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}