package com.gwonsystem.backend.controller;

import com.gwonsystem.backend.dto.LoginRequest;
import com.gwonsystem.backend.dto.LoginResponse;
import com.gwonsystem.backend.dto.RegisterRequest;
import com.gwonsystem.backend.dto.RoleUpdateRequest;
import com.gwonsystem.backend.entity.Member;
import com.gwonsystem.backend.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@Tag(name = "Member API", description = "회원 가입, 로그인 및 권한 관리 API")
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MemberController {

    private final MemberService memberService;

    // 1. 신규 회원가입 (일반회원 등록)
    @Operation(summary = "신규 회원가입", description = "새로운 일반회원 계정을 생성합니다.")
    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        Member saved = memberService.register(request);
        return ResponseEntity.ok("회원가입이 완료되었습니다. 회원 ID: " + saved.getId());
    }

    // 2. 로그인 API (LoginResponse 명시하여 스웨거 문서화 및 필드 보장)
    @Operation(summary = "로그인", description = "아이디와 비밀번호를 검증하고 회원 프로필 정보(성명, 권한 등)를 반환합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "로그인 실패 (아이디/비밀번호 불일치)")
    })
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

    // 3. 슈퍼바이저의 권한 승격/부여 API (정회원 승인 또는 관리자 부여)
    @Operation(summary = "회원 등급 권한 변경", description = "관리자가 회원의 등급 권한을 변경합니다.")
    @PatchMapping("/{memberId}/role")
    public ResponseEntity<String> updateRole(
            @PathVariable Long memberId,
            @Valid @RequestBody RoleUpdateRequest request) {
        memberService.updateMemberRole(memberId, request);
        return ResponseEntity.ok("해당 회원의 등급 권한이 [" + request.getTargetRole() + "] (으)로 변경되었습니다.");
    }
}