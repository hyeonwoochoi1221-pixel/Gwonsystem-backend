package com.gwonsystem.backend.service;

import com.gwonsystem.backend.dto.LoginResponse;
import com.gwonsystem.backend.dto.RegisterRequest;
import com.gwonsystem.backend.dto.RoleUpdateRequest;
import com.gwonsystem.backend.entity.Member;
import com.gwonsystem.backend.entity.Role;
import com.gwonsystem.backend.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    // 1. 로그인 검증 및 DTO 반환 (DB first_name, last_name, role 문자열 매핑)
    @Transactional(readOnly = true)
    public LoginResponse login(String username, String rawPassword) {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 아이디입니다."));

        // 현재 평문 비교 기준 (추후 BCryptPasswordEncoder 적용 시 matches() 사용)
        if (!member.getPassword().equals(rawPassword)) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // member.getRole()이 Role Enum 객체이므로 안전하게 .name()으로 String 변환
        String roleStr = (member.getRole() != null) ? member.getRole().name() : Role.ROLE_ASSOCIATE.name();

        return LoginResponse.builder()
                .username(member.getUsername())
                .lastName(member.getLastName())
                .firstName(member.getFirstName())
                .role(roleStr)
                .build();
    }

    // 2. 신규 회원가입 (기본 일반회원 ROLE_ASSOCIATE 발급)
    @Transactional
    public Member register(RegisterRequest req) {
        if (memberRepository.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        if (memberRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }
        if (memberRepository.existsByPhone(req.getPhone())) {
            throw new IllegalArgumentException("이미 등록된 전화번호입니다.");
        }

        Member member = new Member();
        member.setUsername(req.getUsername());
        member.setPassword(req.getPassword());
        member.setEmail(req.getEmail());
        member.setLastName(req.getLastName());
        member.setFirstName(req.getFirstName());
        member.setPhone(req.getPhone());
        member.setZipcode(req.getZipcode());
        member.setAddress(req.getAddress());
        member.setDetailAddress(req.getDetailAddress());

        member.setGender(req.getGender());
        member.setBirthDate(req.getBirthDate());
        member.setWorkplaceName(req.getWorkplaceName());
        member.setDepartmentName(req.getDepartmentName());
        member.setPosition(req.getPosition());
        member.setWorkplacePhone(req.getWorkplacePhone());

        member.setRole(Role.ROLE_ASSOCIATE);
        member.setTermsAgreed(req.getTermsAgreed());
        member.setPrivacyAgreed(req.getPrivacyAgreed());
        member.setMarketingAgreed(req.getMarketingAgreed() != null && req.getMarketingAgreed());
        member.setPrivacyAgreedAt(LocalDateTime.now());

        return memberRepository.save(member);
    }

    // 3. 슈퍼바이저 전용 계정 권한 갱신
    @Transactional
    public void updateMemberRole(Long targetMemberId, RoleUpdateRequest req) {
        Member member = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        member.changeRole(req.getTargetRole());
    }
}