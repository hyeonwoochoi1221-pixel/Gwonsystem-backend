package com.gwonsystem.backend.controller;

import com.gwonsystem.backend.dto.LoginRequest;
import com.gwonsystem.backend.dto.RegisterRequest;
import com.gwonsystem.backend.dto.RoleUpdateRequest;
import com.gwonsystem.backend.entity.Member;
import com.gwonsystem.backend.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MemberController {

    private final MemberService memberService;

    // 1. 신규 회원가입 (일반회원 등록)
    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        Member saved = memberService.register(request);
        return ResponseEntity.ok("회원가입이 완료되었습니다. 회원 ID: " + saved.getId());
    }

    // 2. 로그인 API (403 해결용 추가)
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            Member member = memberService.login(request.getUsername(), request.getPassword());

            // 프론트엔드에서 사용할 유저명 및 권한 정보 반환
            Map<String, Object> response = new HashMap<>();
            response.put("message", "로그인 성공");
            response.put("username", member.getUsername());
            response.put("role", member.getRole().name()); // ROLE_SUPERVISOR, ROLE_REGULAR 등

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // 3. 슈퍼바이저의 권한 승격/부여 API (정회원 승인 또는 관리자 부여)
    @PatchMapping("/{memberId}/role")
    public ResponseEntity<String> updateRole(
            @PathVariable Long memberId,
            @Valid @RequestBody RoleUpdateRequest request) {
        memberService.updateMemberRole(memberId, request);
        return ResponseEntity.ok("해당 회원의 등급 권한이 [" + request.getTargetRole() + "] (으)로 변경되었습니다.");
    }
}