package com.gwonsystem.backend.controller;

import com.gwonsystem.backend.dto.EmailSendRequest;
import com.gwonsystem.backend.dto.EmailVerifyRequest;
import com.gwonsystem.backend.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@Tag(name = "Email API", description = "이메일 6자리 인증코드 발송 및 검증 API")
@RestController
@RequestMapping("/api/members/email")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EmailController {

    private final EmailService emailService;

    // 1. 회원가입 이메일 6자리 인증코드 발송
    @Operation(summary = "이메일 인증코드 발송", description = "입력받은 이메일로 6자리 난수 인증코드를 발송합니다.")
    @PostMapping("/send-code")
    public ResponseEntity<?> sendCode(@Valid @RequestBody EmailSendRequest request) {
        try {
            emailService.sendVerificationCode(request.getEmail());
            return ResponseEntity.ok(Map.of("message", "인증번호가 이메일로 발송되었습니다. (유효시간 3분)"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    // 2. 이메일 6자리 인증코드 검증
    @Operation(summary = "이메일 인증코드 검증", description = "전송된 6자리 인증코드가 유효한지 일치 여부를 판별합니다.")
    @PostMapping("/verify-code")
    public ResponseEntity<?> verifyCode(@Valid @RequestBody EmailVerifyRequest request) {
        try {
            boolean isVerified = emailService.verifyCode(request.getEmail(), request.getCode());
            return ResponseEntity.ok(Map.of(
                    "verified", isVerified,
                    "message", "이메일 인증이 완료되었습니다."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "verified", false,
                    "message", e.getMessage()
            ));
        }
    }
}