// src/main/java/com/gwonsystem/backend/security/OAuth2SuccessHandler.java
package com.gwonsystem.backend.security;

import com.gwonsystem.backend.entity.Member;
import com.gwonsystem.backend.repository.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final MemberRepository memberRepository;

    // 환경변수로 프론트엔드 주소 관리 (기본값: http://localhost:5173)
    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = token.getPrincipal();
        String registrationId = token.getAuthorizedClientRegistrationId(); // "google" 또는 "naver"

        String email = "";
        String firstName = "";
        String lastName = "";
        String providerId = "";

        // 1. Google 계정 프로필 추출
        if ("google".equalsIgnoreCase(registrationId)) {
            email = oAuth2User.getAttribute("email");
            providerId = oAuth2User.getAttribute("sub");
            String givenName = oAuth2User.getAttribute("given_name");
            String familyName = oAuth2User.getAttribute("family_name");
            String name = oAuth2User.getAttribute("name");

            firstName = givenName != null ? givenName.trim() : "";
            lastName = familyName != null ? familyName.trim() : "";

            // given_name, family_name이 없고 통짜 name만 온 경우 분리
            if (firstName.isEmpty() && lastName.isEmpty() && name != null && !name.isBlank()) {
                String cleanName = name.trim();
                if (cleanName.length() > 1) {
                    lastName = cleanName.substring(0, 1);
                    firstName = cleanName.substring(1);
                } else {
                    firstName = cleanName;
                }
            }
        }
        // 2. Naver 계정 프로필 추출
        else if ("naver".equalsIgnoreCase(registrationId)) {
            Map<String, Object> attributes = oAuth2User.getAttributes();
            Map<String, Object> naverAccount = (Map<String, Object>) attributes.get("response");

            if (naverAccount != null) {
                email = (String) naverAccount.get("email");
                providerId = (String) naverAccount.get("id");
                String fullName = (String) naverAccount.get("name");

                if (fullName != null && !fullName.isBlank()) {
                    String cleanName = fullName.trim();
                    if (cleanName.length() > 1) {
                        lastName = cleanName.substring(0, 1);
                        firstName = cleanName.substring(1);
                    } else {
                        firstName = cleanName;
                    }
                }
            }
        }

        final String finalEmail = (email != null) ? email.trim() : "";
        final String finalLastName = (lastName != null) ? lastName.trim() : "";
        final String finalFirstName = (firstName != null) ? firstName.trim() : "";
        final String finalProvider = registrationId.toUpperCase();
        final String finalProviderId = (providerId != null) ? providerId.trim() : "";

        log.info("OAuth2 인증 성공 [{}]: email={}, providerId={}", finalProvider, finalEmail, finalProviderId);

        // 동일한 이메일을 가진 기존 회원 조회
        Optional<Member> existingMemberOpt = !finalEmail.isEmpty()
                ? memberRepository.findByEmail(finalEmail)
                : Optional.empty();

        if (existingMemberOpt.isPresent()) {
            // [A] 이미 가입된 회원이 존재할 때: 소셜 연동 정보 업데이트 후 메인 콜백(/oauth/callback)으로 이동
            Member member = existingMemberOpt.get();
            member.linkSocialAccount(finalProvider, finalProviderId);

            if (member.getLastName() == null || member.getLastName().isBlank()) {
                member.setLastName(finalLastName);
            }
            if (member.getFirstName() == null || member.getFirstName().isBlank()) {
                member.setFirstName(finalFirstName);
            }
            memberRepository.save(member);

            String redirectUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/oauth/callback")
                    .queryParam("username", member.getUsername())
                    .queryParam("role", member.getRole().name())
                    .queryParam("lastName", URLEncoder.encode(member.getLastName() != null ? member.getLastName() : "", StandardCharsets.UTF_8))
                    .queryParam("firstName", URLEncoder.encode(member.getFirstName() != null ? member.getFirstName() : "", StandardCharsets.UTF_8))
                    .build().toUriString();

            log.info("기존 회원 로그인 연동 성공 -> 리다이렉트: {}", redirectUrl);
            getRedirectStrategy().sendRedirect(request, response, redirectUrl);
        } else {
            // [B] 기존 회원이 없을 때: 빈 계정을 만들지 않고 소셜 정보를 들고 회원가입 화면(/register)으로 이동
            String registerRedirectUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/register")
                    .queryParam("socialEmail", URLEncoder.encode(finalEmail, StandardCharsets.UTF_8))
                    .queryParam("socialLastName", URLEncoder.encode(finalLastName, StandardCharsets.UTF_8))
                    .queryParam("socialFirstName", URLEncoder.encode(finalFirstName, StandardCharsets.UTF_8))
                    .queryParam("socialProvider", finalProvider)
                    .queryParam("socialProviderId", URLEncoder.encode(finalProviderId, StandardCharsets.UTF_8))
                    .build().toUriString();

            log.info("신규 소셜 회원 감지 -> 회원가입 화면 리다이렉트: {}", registerRedirectUrl);
            getRedirectStrategy().sendRedirect(request, response, registerRedirectUrl);
        }
    }
}