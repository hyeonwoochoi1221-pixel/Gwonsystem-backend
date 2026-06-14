package com.gwonsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "test_users") // Supabase 테이블명과 일치시켜야 합니다.
@Getter
@Setter
@NoArgsConstructor
public class TestUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // SERIAL 타입에 맞춤
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 100, unique = true)
    private String email;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}