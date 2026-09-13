package com.gwonsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String username;
    private String firstName;
    private String lastName;
    private String role; // JSON 직렬화 시 "ROLE_ASSOCIATE" 등의 문자열로 전달
}