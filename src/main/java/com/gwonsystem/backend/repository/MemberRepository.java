package com.gwonsystem.backend.repository;

import com.gwonsystem.backend.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByUsername(String username);
    Optional<Member> findByEmail(String email);

    // 🌟 1. 인적사항(성, 이름, 휴대폰 번호, 이메일) 일치 회원 조회 (아이디 찾기용)
    Optional<Member> findByLastNameAndFirstNameAndPhoneAndEmail(
            String lastName, String firstName, String phone, String email
    );

    // 🌟 2. 아이디와 이메일 동시 일치 검증 (비밀번호 재설정용)
    Optional<Member> findByUsernameAndEmail(String username, String email);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
}