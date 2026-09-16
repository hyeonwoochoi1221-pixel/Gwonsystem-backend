package com.gwonsystem.backend.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 정회원(REGULAR) 및 최고관리자(SUPERVISOR)만 업로드/삭제가 가능하도록 제한하는 통합 보안 어노테이션
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('REGULAR')") // RoleHierarchy에 의해 SUPERVISOR도 자동 통과됨
public @interface RequireContentManager {
}