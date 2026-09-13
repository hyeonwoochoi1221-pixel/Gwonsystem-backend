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

    // [필수] 로그인 계정 정보
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    // [필수] 기본 인적사항
    @Column(nullable = false, length = 20)
    private String lastName;

    @Column(nullable = false, length = 50)
    private String firstName;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    // [선택] 성별, 생년월일
    @Column(length = 10)
    private String gender;

    private LocalDate birthDate;

    // [필수] 주소 정보
    @Column(nullable = false, length = 10)
    private String zipcode;

    @Column(nullable = false)
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

    // [개인정보보호법 준수] 동의 컬럼
    @Column(nullable = false)
    private Boolean termsAgreed = false;

    @Column(nullable = false)
    private Boolean privacyAgreed = false;

    @Column(nullable = false)
    private Boolean marketingAgreed = false;

    private LocalDateTime privacyAgreedAt = LocalDateTime.now();

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    // 슈퍼바이저가 타 계정의 권한을 변경할 수 있는 도메인 메서드
    public void changeRole(Role newRole) {
        this.role = newRole;
        this.updatedAt = LocalDateTime.now();
    }
}