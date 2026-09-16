// src/main/java/com/gwonsystem/backend/dto/RegisterRequest.java
package com.gwonsystem.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
public class RegisterRequest {

    // [필수 항목]
    @NotBlank(message = "아이디는 필수 항목입니다.")
    @Size(min = 4, max = 20, message = "아이디는 4~20자 사이여야 합니다.")
    private String username;

    @NotBlank(message = "비밀번호는 필수 항목입니다.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$",
            message = "비밀번호는 8자 이상, 영문, 숫자, 특수문자를 포함해야 합니다.")
    private String password;

    @NotBlank(message = "이메일은 필수 항목입니다.")
    @Email(message = "유효한 이메일 형식이 아닙니다.")
    private String email;

    @NotBlank(message = "성은 필수 항목입니다.")
    private String lastName;

    @NotBlank(message = "이름은 필수 항목입니다.")
    private String firstName;

    @NotBlank(message = "휴대폰 번호는 필수 항목입니다.")
    private String phone;

    @NotBlank(message = "우편번호는 필수 항목입니다.")
    private String zipcode;

    @NotBlank(message = "주소는 필수 항목입니다.")
    private String address;

    // [선택 항목]
    private String detailAddress;
    private String gender;
    private LocalDate birthDate;
    private String workplaceName;
    private String departmentName;
    private String position;
    private String workplacePhone;

    // 🌟 소셜 로그인 연동 정보 (선택)
    private String provider;
    private String providerId;

    // [개인정보보호법 필수/선택 동의 여부]
    @AssertTrue(message = "서비스 이용약관에 동의해야 회원가입이 가능합니다.")
    private Boolean termsAgreed;

    @AssertTrue(message = "개인정보 수집 및 이용에 동의해야 회원가입이 가능합니다.")
    private Boolean privacyAgreed;

    private Boolean marketingAgreed = false;
}