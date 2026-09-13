package com.gwonsystem.backend.entity;

public enum Role {
    ROLE_ASSOCIATE,   // 일반회원 (준회원)
    ROLE_REGULAR,     // 정회원 (정회원 혜택/공지 열람 권한)
    ROLE_SUPERVISOR   // 최고관리자 (신규 계정에 정회원/관리자 권한 부여 가능)
}