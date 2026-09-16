package com.gwonsystem.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FindUsernameRequest {

    @NotBlank(message = "성은 필수 입력 항목입니다.")
    private String lastName;

    @NotBlank(message = "이름은 필수 입력 항목입니다.")
    private String firstName;

    @NotBlank(message = "휴대폰 번호는 필수 입력 항목입니다.")
    private String phone;

    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;
}