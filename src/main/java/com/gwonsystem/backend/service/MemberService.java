// src/main/java/com/gwonsystem/backend/service/MemberService.java
package com.gwonsystem.backend.service;

import com.gwonsystem.backend.dto.*;
import com.gwonsystem.backend.entity.Member;
import com.gwonsystem.backend.entity.Role;
import com.gwonsystem.backend.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    // 1. 로그인 검증
    @Transactional(readOnly = true)
    public LoginResponse login(String username, String rawPassword) {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 아이디입니다."));

        boolean passwordMatches = passwordEncoder.matches(rawPassword, member.getPassword())
                || member.getPassword().equals(rawPassword);

        if (!passwordMatches) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        String roleStr = (member.getRole() != null) ? member.getRole().name() : Role.ROLE_ASSOCIATE.name();

        return LoginResponse.builder()
                .username(member.getUsername())
                .lastName(member.getLastName())
                .firstName(member.getFirstName())
                .role(roleStr)
                .build();
    }

    // 1-1. 아이디 중복 여부 확인
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return memberRepository.existsByUsername(username);
    }

    // 🌟 1-2. [회원가입] 이메일 인증번호 발송
    public void sendEmailVerificationCode(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일 주소를 입력해 주세요.");
        }
        if (memberRepository.existsByEmail(email.trim())) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }
        emailService.sendVerificationCode(email.trim());
    }

    // 🌟 1-3. [공통] 이메일 인증번호 6자리 일치 검증
    public boolean verifyEmailCode(String email, String code) {
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            throw new IllegalArgumentException("이메일과 인증번호를 모두 입력해 주세요.");
        }
        return emailService.verifyCode(email.trim(), code.trim());
    }

    // 2. 신규 회원가입
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
        member.setPassword(passwordEncoder.encode(req.getPassword()));
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

        if (req.getProvider() != null && !req.getProvider().isBlank()) {
            member.linkSocialAccount(req.getProvider(), req.getProviderId());
        }

        member.setRole(Role.ROLE_ASSOCIATE);
        member.setTermsAgreed(req.getTermsAgreed());
        member.setPrivacyAgreed(req.getPrivacyAgreed());
        member.setMarketingAgreed(req.getMarketingAgreed() != null && req.getMarketingAgreed());
        member.setPrivacyAgreedAt(LocalDateTime.now());

        return memberRepository.save(member);
    }

    // 🌟 2-1. [아이디 찾기]: 인적사항 일치 시 이메일로 아이디 전송
    @Transactional(readOnly = true)
    public void findUsernameAndSendEmail(FindUsernameRequest req) {
        Member member = memberRepository.findByLastNameAndFirstNameAndPhoneAndEmail(
                req.getLastName().trim(),
                req.getFirstName().trim(),
                req.getPhone().trim(),
                req.getEmail().trim()
        ).orElseThrow(() -> new IllegalArgumentException("입력하신 인적사항과 일치하는 회원을 찾을 수 없습니다."));

        String fullName = (member.getLastName() != null ? member.getLastName() : "") +
                (member.getFirstName() != null ? member.getFirstName() : "");

        emailService.sendFoundUsernameEmail(member.getEmail(), fullName, member.getUsername());
    }

    // 🌟 2-2. [비밀번호 찾기 1단계]: 아이디 & 이메일 검증 후 인증코드 발송
    @Transactional(readOnly = true)
    public void sendPasswordResetVerification(PasswordResetSendCodeRequest req) {
        memberRepository.findByUsernameAndEmail(req.getUsername().trim(), req.getEmail().trim())
                .orElseThrow(() -> new IllegalArgumentException("일치하는 회원 정보를 찾을 수 없습니다. 아이디와 이메일을 확인해 주세요."));

        emailService.sendPasswordResetCode(req.getEmail().trim());
    }

    // 🌟 2-3. [비밀번호 찾기 2단계]: 1차 인증 상태 또는 코드 검증 후 새 비밀번호로 암호화 변경
    @Transactional
    public void resetPasswordWithCode(PasswordResetConfirmRequest req) {
        Member member = memberRepository.findByUsernameAndEmail(req.getUsername().trim(), req.getEmail().trim())
                .orElseThrow(() -> new IllegalArgumentException("일치하는 회원 정보를 찾을 수 없습니다."));

        emailService.consumeVerification(req.getEmail().trim(), req.getCode() != null ? req.getCode().trim() : "");

        member.setPassword(passwordEncoder.encode(req.getNewPassword()));
        memberRepository.save(member);
    }

    // 🌟 2-4. [개인정보 수정 1단계]: 현재 비밀번호 일치 여부 검증
    @Transactional(readOnly = true)
    public boolean verifyPassword(String username, String rawPassword) {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        boolean matches = passwordEncoder.matches(rawPassword, member.getPassword())
                || member.getPassword().equals(rawPassword);

        if (!matches) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        return true;
    }

    // 🌟 2-5. [개인정보 수정 2단계]: 본인 정보 단건 조회
    @Transactional(readOnly = true)
    public Member findByUsername(String username) {
        return memberRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
    }

    // 🌟 2-6. [개인정보 수정 3단계]: 본인 프로필 상세 수정 (비밀번호 변경 포함)
    @Transactional
    public Member updateMemberSelfProfile(String username, MemberAdminCreateRequest req) {
        Member member = findByUsername(username);

        // 새 비밀번호가 입력된 경우에만 단방향 해시 암호화 후 반영
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            member.setPassword(passwordEncoder.encode(req.getPassword()));
        }

        // 인적사항 및 주소/소속 정보 업데이트
        if (req.getLastName() != null) member.setLastName(req.getLastName().trim());
        if (req.getFirstName() != null) member.setFirstName(req.getFirstName().trim());
        if (req.getPhone() != null) member.setPhone(req.getPhone().trim());
        if (req.getGender() != null) member.setGender(req.getGender());
        if (req.getBirthDate() != null) member.setBirthDate(req.getBirthDate());

        if (req.getZipcode() != null) member.setZipcode(req.getZipcode());
        if (req.getAddress() != null) member.setAddress(req.getAddress());
        if (req.getDetailAddress() != null) member.setDetailAddress(req.getDetailAddress());

        if (req.getWorkplaceName() != null) member.setWorkplaceName(req.getWorkplaceName());
        if (req.getDepartmentName() != null) member.setDepartmentName(req.getDepartmentName());
        if (req.getPosition() != null) member.setPosition(req.getPosition());
        if (req.getWorkplacePhone() != null) member.setWorkplacePhone(req.getWorkplacePhone());

        return memberRepository.save(member);
    }

    // 3. 권한 / 부서 / 직급 부분 수정 (관리자용)
    @Transactional
    public Member updateMemberRole(Long targetMemberId, MemberAdminCreateRequest req) {
        Member member = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        if (req.getRole() != null) {
            member.changeRole(req.getRole());
        }

        if (req.getDepartmentName() != null || req.getPosition() != null) {
            String newDept = req.getDepartmentName() != null ? req.getDepartmentName() : member.getDepartmentName();
            String newPos = req.getPosition() != null ? req.getPosition() : member.getPosition();
            member.updateDepartmentAndPosition(newDept, newPos);
        }

        return member;
    }

    // 4. 전체 회원 조회
    @Transactional(readOnly = true)
    public List<Member> getAllMembers() {
        return memberRepository.findAll();
    }

    // 5. 관리자 회원 생성
    @Transactional
    public Member createMemberByAdmin(MemberAdminCreateRequest req) {
        if (memberRepository.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        if (memberRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("이미 등록된 이메일입니다.");
        }
        if (req.getPhone() != null && !req.getPhone().isBlank() && memberRepository.existsByPhone(req.getPhone())) {
            throw new IllegalArgumentException("이미 등록된 전화번호입니다.");
        }

        String rawPw = (req.getPassword() != null && !req.getPassword().isBlank()) ? req.getPassword() : "12345678";

        Member member = new Member();
        member.setUsername(req.getUsername());
        member.setPassword(passwordEncoder.encode(rawPw));
        member.setEmail(req.getEmail());
        member.setLastName(req.getLastName());
        member.setFirstName(req.getFirstName());
        member.setPhone(req.getPhone());
        member.setGender(req.getGender());
        member.setBirthDate(req.getBirthDate());

        member.setDepartmentName(req.getDepartmentName());
        member.setPosition(req.getPosition());
        member.setWorkplaceName(req.getWorkplaceName());
        member.setWorkplacePhone(req.getWorkplacePhone());

        member.setZipcode(req.getZipcode());
        member.setAddress(req.getAddress());
        member.setDetailAddress(req.getDetailAddress());

        member.setRole(req.getRole() != null ? req.getRole() : Role.ROLE_ASSOCIATE);
        member.setTermsAgreed(true);
        member.setPrivacyAgreed(true);
        member.setMarketingAgreed(false);
        member.setPrivacyAgreedAt(LocalDateTime.now());

        return memberRepository.save(member);
    }

    // 6. 관리자 회원 정보 수정
    @Transactional
    public Member updateMemberByAdmin(Long memberId, MemberAdminCreateRequest req) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        if (req.getEmail() != null && !member.getEmail().equals(req.getEmail()) && memberRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("이미 등록된 이메일입니다.");
        }

        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            member.setPassword(passwordEncoder.encode(req.getPassword()));
        }

        member.updateAdminProfile(
                req.getLastName(),
                req.getFirstName(),
                req.getEmail(),
                req.getPhone(),
                req.getDepartmentName(),
                req.getPosition(),
                req.getWorkplaceName(),
                req.getRole()
        );

        return member;
    }

    // 7. 관리자 회원 삭제
    @Transactional
    public void deleteMemberByAdmin(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        memberRepository.delete(member);
    }
}