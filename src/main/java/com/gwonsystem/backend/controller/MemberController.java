package com.gwonsystem.backend.controller;

import com.gwonsystem.backend.dto.*;
import com.gwonsystem.backend.entity.Member;
import com.gwonsystem.backend.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Member API", description = "회원 가입, 로그인, 아이디/비밀번호 찾기 및 권한 관리 API")
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MemberController {

    private final MemberService memberService;

    // 1. 신규 회원가입
    @Operation(summary = "신규 회원가입", description = "새로운 일반회원 계정을 생성합니다.")
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            Member saved = memberService.register(request);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "회원가입이 완료되었습니다.");
            response.put("memberId", saved.getId());
            response.put("username", saved.getUsername());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = Collections.singletonMap("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // 1-1. 아이디 중복 확인
    @Operation(summary = "아이디 중복 확인", description = "신규 회원가입 시 아이디 중복 여부를 조회합니다.")
    @GetMapping("/check-username")
    public ResponseEntity<Map<String, Object>> checkUsername(@RequestParam String username) {
        boolean exists = memberService.existsByUsername(username);
        Map<String, Object> response = new HashMap<>();
        response.put("exists", exists);
        response.put("message", exists ? "이미 존재하는 아이디입니다." : "사용 가능한 아이디입니다.");
        return ResponseEntity.ok(response);
    }

    // 2. 로그인 API
    @Operation(summary = "로그인", description = "아이디와 비밀번호를 검증하고 회원 프로필 정보를 반환합니다.")
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = memberService.login(request.getUsername(), request.getPassword());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = Collections.singletonMap("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // 2-1. 아이디 찾기 API (인적사항 확인 후 이메일로 발송)
    @Operation(summary = "아이디 찾기", description = "성명, 연락처, 이메일을 대조하여 일치할 경우 해당 이메일로 아이디를 전송합니다.")
    @PostMapping("/find-username")
    public ResponseEntity<?> findUsername(@Valid @RequestBody FindUsernameRequest request) {
        try {
            memberService.findUsernameAndSendEmail(request);
            return ResponseEntity.ok(Map.of("message", "가입하신 이메일로 아이디 정보가 발송되었습니다."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 2-2. 비밀번호 재설정 1단계 (인증코드 발송)
    @Operation(summary = "비밀번호 재설정 인증코드 발송", description = "아이디와 이메일이 일치하는지 검증 후 6자리 인증코드를 전송합니다.")
    @PostMapping("/password/send-code")
    public ResponseEntity<?> sendPasswordResetCode(@Valid @RequestBody PasswordResetSendCodeRequest request) {
        try {
            memberService.sendPasswordResetVerification(request);
            return ResponseEntity.ok(Map.of("message", "비밀번호 재설정 인증번호가 이메일로 발송되었습니다."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 2-3. 비밀번호 재설정 2단계 (인증코드 검증 및 비밀번호 변경)
    @Operation(summary = "비밀번호 재설정 확정", description = "인증번호를 확인하고 새로운 비밀번호로 변경합니다.")
    @PostMapping("/password/reset")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
        try {
            memberService.resetPasswordWithCode(request);
            return ResponseEntity.ok(Map.of("message", "비밀번호가 성공적으로 변경되었습니다. 새로운 비밀번호로 로그인해 주세요."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 3. [관리자용] 권한/부서/직급 부분 변경 API
    @Operation(summary = "회원 등급 권한 및 조직정보 변경", description = "관리자가 회원의 등급 권한(ROLE), 부서, 직급을 변경합니다.")
    @PatchMapping("/{memberId}/role")
    public ResponseEntity<?> updateRole(
            @PathVariable Long memberId,
            @RequestBody MemberAdminCreateRequest request) {
        Member updatedMember = memberService.updateMemberRole(memberId, request);
        return ResponseEntity.ok(Map.of(
                "message", "회원 정보가 성공적으로 수정되었습니다.",
                "memberId", updatedMember.getId(),
                "role", updatedMember.getRole(),
                "departmentName", updatedMember.getDepartmentName() != null ? updatedMember.getDepartmentName() : "",
                "position", updatedMember.getPosition() != null ? updatedMember.getPosition() : ""
        ));
    }

    // 4. [관리자용] 전체 회원 목록 조회
    @Operation(summary = "전체 회원 목록 조회", description = "슈퍼바이저 관리자 페이지에서 전체 회원 명단을 조회합니다.")
    @GetMapping
    public ResponseEntity<List<Member>> getAllMembers() {
        return ResponseEntity.ok(memberService.getAllMembers());
    }

    // 5. [관리자용] 회원 직접 생성 API
    @Operation(summary = "관리자 회원 신규 등록", description = "슈퍼바이저가 회원을 직접 등록합니다.")
    @PostMapping("/admin/create")
    public ResponseEntity<?> createMemberByAdmin(@RequestBody MemberAdminCreateRequest request) {
        Member saved = memberService.createMemberByAdmin(request);
        return ResponseEntity.ok(Map.of(
                "message", "회원 계정이 성공적으로 생성되었습니다.",
                "memberId", saved.getId(),
                "username", saved.getUsername()
        ));
    }

    // 6. [관리자용] 회원 상세 정보 수정 API
    @Operation(summary = "관리자 회원 상세 정보 수정", description = "슈퍼바이저가 회원의 인적사항, 직급, 권한 등을 전체 수정합니다.")
    @PutMapping("/admin/{memberId}")
    public ResponseEntity<?> updateMemberByAdmin(
            @PathVariable Long memberId,
            @RequestBody MemberAdminCreateRequest request
    ) {
        Member updated = memberService.updateMemberByAdmin(memberId, request);
        return ResponseEntity.ok(Map.of(
                "message", "회원 정보가 성공적으로 수정되었습니다.",
                "memberId", updated.getId(),
                "username", updated.getUsername()
        ));
    }

    // 7. [관리자용] 회원 삭제 API
    @Operation(summary = "관리자 회원 삭제", description = "슈퍼바이저가 특정 회원을 영구 삭제합니다.")
    @DeleteMapping("/admin/{memberId}")
    public ResponseEntity<?> deleteMemberByAdmin(@PathVariable Long memberId) {
        memberService.deleteMemberByAdmin(memberId);
        return ResponseEntity.ok(Map.of(
                "message", "회원이 정상적으로 삭제되었습니다.",
                "deletedMemberId", memberId
        ));
    }
}