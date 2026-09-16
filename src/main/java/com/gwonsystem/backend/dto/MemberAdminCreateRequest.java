package com.gwonsystem.backend.dto;

import com.gwonsystem.backend.entity.Role;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class MemberAdminCreateRequest {

    // 계정 정보
    private String username;
    private String password;
    private String email;

    // 기본 인적 사항
    private String lastName;
    private String firstName;
    private String phone;
    private String gender;
    private LocalDate birthDate;

    // 조직 및 직급 정보
    private String departmentName;
    private String position;
    private String workplaceName;
    private String workplacePhone;

    // 주소 정보
    private String zipcode;
    private String address;
    private String detailAddress;

    // 권한 등급 (기본값: 준회원)
    private Role role;
}