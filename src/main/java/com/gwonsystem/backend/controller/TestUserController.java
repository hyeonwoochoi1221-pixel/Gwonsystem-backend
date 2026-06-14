package com.gwonsystem.backend.controller;

import com.gwonsystem.backend.dto.TestUserRequest;
import com.gwonsystem.backend.entity.TestUser;
import com.gwonsystem.backend.repository.TestUserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000") // 프론트엔드 포트 번호에 맞게 설정 (CORS 허용)
public class TestUserController {

    private final TestUserRepository testUserRepository;

    @PostMapping
    public ResponseEntity<TestUser> createUser(@Valid @RequestBody TestUserRequest request) {
        // DTO 데이터를 Entity로 변환
        TestUser user = new TestUser();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());

        // Supabase DB에 저장
        TestUser savedUser = testUserRepository.save(user);

        // 저장된 결과 반환
        return ResponseEntity.ok(savedUser);
    }
}