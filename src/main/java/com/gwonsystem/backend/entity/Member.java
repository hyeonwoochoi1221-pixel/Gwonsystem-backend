package com.gwonsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "members")
@Getter
@Setter
@NoArgsConstructor
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 로그인 계정 정보 (일반: 아이디, 소셜: google_xxx / naver_xxx)
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    // 일반 회원은 필수이나 소셜 계정은 비밀번호가 없으므로 nullable 허용
    @Column(nullable = true)
    private String password;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    // 🌟 소셜 로그인 연동 정보
    @Column(length = 20)
    private String provider; // "LOCAL", "GOOGLE", "NAVER"

    @Column(length = 100)
    private String providerId; // 소셜 플랫폼의 고유 회원 번호 (sub 또는 id)

    // 기본 인적사항
    @Column(nullable = false, length = 20)
    private String lastName;

    @Column(nullable = false, length = 50)
    private String firstName;

    // 소셜 로그인 프로필에는 전화번호가 없으므로 null 허용
    @Column(nullable = true, length = 20)
    private String phone;

    // [선택] 성별, 생년월일
    @Column(length = 10)
    private String gender;

    private LocalDate birthDate;

    // 소셜 로그인 시에는 주소 정보가 없으므로 null 허용
    @Column(nullable = true, length = 10)
    private String zipcode;

    @Column(nullable = true)
    private String address;

    private String detailAddress;

    // [선택] 직장 및 소속 정보
    private String workplaceName;
    private String departmentName;
    private String position;
    private String workplacePhone;

    // 권한 등급
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role = Role.ROLE_ASSOCIATE;

    // 약관 동의 (소셜 로그인 기본값 false 처리 및 null 방지)
    @Column(nullable = false)
    private Boolean termsAgreed = true;

    @Column(nullable = false)
    private Boolean privacyAgreed = true;

    @Column(nullable = false)
    private Boolean marketingAgreed = false;

    private LocalDateTime privacyAgreedAt = LocalDateTime.now();

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    // 🌟 소셜 계정 연동 메서드
    public void linkSocialAccount(String provider, String providerId) {
        this.provider = provider;
        this.providerId = providerId;
        this.updatedAt = LocalDateTime.now();
    }

    // 1. 슈퍼바이저 전용: 계정 권한 등급 단독 변경
    public void changeRole(Role newRole) {
        if (newRole != null) {
            this.role = newRole;
            this.updatedAt = LocalDateTime.now();
        }
    }

    // 2. 슈퍼바이저 전용: 부서명 및 직급 변경
    public void updateDepartmentAndPosition(String departmentName, String position) {
        this.departmentName = departmentName;
        this.position = position;
        this.updatedAt = LocalDateTime.now();
    }

    // 3. 슈퍼바이저 전용: 회원 상세 인적/소속 정보 및 권한 일괄 수정
    public void updateAdminProfile(
            String lastName,
            String firstName,
            String email,
            String phone,
            String departmentName,
            String position,
            String workplaceName,
            Role role
    ) {
        if (lastName != null) this.lastName = lastName;
        if (firstName != null) this.firstName = firstName;
        if (email != null) this.email = email;
        this.phone = phone;
        this.departmentName = departmentName;
        this.position = position;
        this.workplaceName = workplaceName;
        if (role != null) this.role = role;
        this.updatedAt = LocalDateTime.now();
    }
}