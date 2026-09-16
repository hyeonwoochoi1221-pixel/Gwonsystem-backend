package com.gwonsystem.backend.repository;

import com.gwonsystem.backend.entity.TestUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestUserRepository extends JpaRepository<TestUser, Long> {
    // 기본적인 save(), findAll() 등은 자동으로 제공됩니다.
}