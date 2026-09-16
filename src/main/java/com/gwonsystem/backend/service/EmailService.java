// src/main/java/com/gwonsystem/backend/service/EmailService.java
package com.gwonsystem.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    // 인증코드 및 인증완료 상태 저장소
    private final Map<String, VerificationEntry> verificationStorage = new ConcurrentHashMap<>();
    private static final int EXPIRATION_MINUTES = 3;
    private static final int VERIFIED_HOLD_MINUTES = 10;

    private static class VerificationEntry {
        String code;
        LocalDateTime expiresAt;

        VerificationEntry(String code, LocalDateTime expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }
    }

    // 이메일 정규화 유틸 (공백 제거 및 소문자 통일)
    private String normalizeEmail(String email) {
        return (email == null) ? "" : email.trim().toLowerCase();
    }

    // 1. 회원가입용 6자리 인증코드 발송
    public void sendVerificationCode(String toEmail) {
        sendOtpMail(toEmail, "REGISTER", "[GWON SYSTEM] 회원가입 이메일 인증번호 안내");
    }

    // 2. 비밀번호 재설정용 6자리 인증코드 발송
    public void sendPasswordResetCode(String toEmail) {
        sendOtpMail(toEmail, "PW_RESET", "[GWON SYSTEM] 비밀번호 재설정 인증번호 안내");
    }

    // 3. 아이디 찾기 완료 안내 메일 전송
    public void sendFoundUsernameEmail(String toEmail, String fullName, String username) {
        String cleanEmail = normalizeEmail(toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(cleanEmail);
            helper.setSubject("[GWON SYSTEM] 회원님의 계정 아이디 안내");

            String htmlContent = "<div style='max-width:540px; margin:0 auto; padding:30px 20px; font-family:sans-serif; background-color:#1c1c24; border-radius:12px; border:1px solid #2e2e38; color:#f1f5f9;'>"
                    + "<div style='text-align:center; margin-bottom:24px;'>"
                    + "<h2 style='color:#0ea5e9; margin:0; font-size:24px;'>GWON SYSTEM</h2>"
                    + "<p style='color:#94a3b8; font-size:13px; margin:6px 0 0;'>계정 정보 조회 결과 안내</p>"
                    + "</div>"
                    + "<div style='background-color:#121216; padding:24px; border-radius:8px; border:1px solid #2e2e38; text-align:center;'>"
                    + "<p style='font-size:14px; color:#cbd5e1; margin:0 0 16px;'>" + fullName + " 회원님의 아이디는 아래와 같습니다.</p>"
                    + "<div style='font-size:24px; font-weight:800; color:#38bdf8; letter-spacing:2px; padding:12px 0; background-color:rgba(14,165,233,0.1); border-radius:6px; display:inline-block; min-width:240px;'>"
                    + username + "</div>"
                    + "</div>"
                    + "<p style='font-size:12px; color:#94a3b8; margin-top:20px; text-align:center;'>로그인 페이지로 이동하여 위 아이디로 로그인해 주세요.</p>"
                    + "</div>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("아이디 조회 안내 메일 발송 완료: {}", cleanEmail);
        } catch (MessagingException e) {
            log.error("메일 발송 실패: {}", e.getMessage());
            throw new IllegalArgumentException("이메일 발송에 실패했습니다. 메일 주소를 다시 확인해 주세요.");
        }
    }

    // 4. 인증코드 일치 검증 (1차 확인 버튼 및 재확인 공용)
    public boolean verifyCode(String email, String inputCode, String type) {
        String cleanEmail = normalizeEmail(email);
        String cleanCode = (inputCode == null) ? "" : inputCode.trim();

        // 이미 1차 인증을 통과하여 VERIFIED 상태로 유지 중인지 확인
        String verifiedKey = "VERIFIED:" + cleanEmail;
        VerificationEntry verifiedEntry = verificationStorage.get(verifiedKey);
        if (verifiedEntry != null) {
            if (LocalDateTime.now().isBefore(verifiedEntry.expiresAt)) {
                log.info("이미 1차 인증을 통과한 이메일입니다: {}", cleanEmail);
                return true;
            } else {
                verificationStorage.remove(verifiedKey);
            }
        }

        // 등록된 OTP 키 탐색 (PW_RESET 또는 REGISTER 교차 지원)
        String matchedKey = null;
        VerificationEntry entry = null;

        String primaryKey = type + ":" + cleanEmail;
        if (verificationStorage.containsKey(primaryKey)) {
            matchedKey = primaryKey;
            entry = verificationStorage.get(primaryKey);
        } else {
            String fallbackType = "REGISTER".equalsIgnoreCase(type) ? "PW_RESET" : "REGISTER";
            String fallbackKey = fallbackType + ":" + cleanEmail;
            if (verificationStorage.containsKey(fallbackKey)) {
                matchedKey = fallbackKey;
                entry = verificationStorage.get(fallbackKey);
            }
        }

        if (entry == null) {
            log.warn("인증 실패 - 키 미존재: email={}, type={}, 현재보관={}", cleanEmail, type, verificationStorage.keySet());
            throw new IllegalArgumentException("인증번호가 발송되지 않았거나 만료되었습니다. 다시 발송해 주세요.");
        }

        if (LocalDateTime.now().isAfter(entry.expiresAt)) {
            verificationStorage.remove(matchedKey);
            log.warn("인증 실패 - 유효시간 초과: key={}, expiresAt={}", matchedKey, entry.expiresAt);
            throw new IllegalArgumentException("인증번호 유효시간(3분)이 초과되었습니다. 다시 발송해 주세요.");
        }

        if (!entry.code.equals(cleanCode)) {
            log.warn("인증 실패 - 코드 불일치: key={}, 입력값={}, 실제값={}", matchedKey, cleanCode, entry.code);
            throw new IllegalArgumentException("인증번호가 일치하지 않습니다. 다시 확인해 주세요.");
        }

        // 인증 통과 시 기존 OTP는 제거하고 10분간 인증 완료(VERIFIED) 상태 부여
        verificationStorage.remove(matchedKey);
        verificationStorage.put(verifiedKey, new VerificationEntry("DONE", LocalDateTime.now().plusMinutes(VERIFIED_HOLD_MINUTES)));
        log.info("인증코드 검증 완료 및 상태 유지 시작: key={}", verifiedKey);
        return true;
    }

    public boolean verifyCode(String email, String inputCode) {
        return verifyCode(email, inputCode, "REGISTER");
    }

    // 5. 최종 비밀번호 변경 시 인증 완료 상태 검증 및 소모
    public void consumeVerification(String email, String code) {
        String cleanEmail = normalizeEmail(email);
        String verifiedKey = "VERIFIED:" + cleanEmail;
        VerificationEntry verifiedEntry = verificationStorage.get(verifiedKey);

        // 이미 1차 인증을 완료한 상태인 경우 소모 후 종료
        if (verifiedEntry != null && LocalDateTime.now().isBefore(verifiedEntry.expiresAt)) {
            verificationStorage.remove(verifiedKey);
            log.info("비밀번호 변경을 위해 인증 상태를 최종 소모했습니다: {}", cleanEmail);
            return;
        }

        // 1차 인증 버튼 없이 바로 변경 요청이 들어온 경우 원본 코드로 검증 후 소모
        verifyCode(cleanEmail, code, "PW_RESET");
        verificationStorage.remove(verifiedKey);
        log.info("비밀번호 변경을 위해 인증코드를 즉시 검증 및 소모했습니다: {}", cleanEmail);
    }

    private void sendOtpMail(String toEmail, String type, String subject) {
        String cleanEmail = normalizeEmail(toEmail);
        String code = generateNumericCode();
        String key = type + ":" + cleanEmail;
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES);

        // 기존에 남아있던 완료 상태나 OTP 제거 후 새로 등록
        verificationStorage.remove("VERIFIED:" + cleanEmail);
        verificationStorage.put(key, new VerificationEntry(code, expiresAt));

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(cleanEmail);
            helper.setSubject(subject);

            String htmlContent = "<div style='max-width:540px; margin:0 auto; padding:30px 20px; font-family:sans-serif; background-color:#1c1c24; border-radius:12px; border:1px solid #2e2e38; color:#f1f5f9;'>"
                    + "<div style='text-align:center; margin-bottom:24px;'>"
                    + "<h2 style='color:#0ea5e9; margin:0; font-size:24px;'>GWON SYSTEM</h2>"
                    + "<p style='color:#94a3b8; font-size:13px; margin:6px 0 0;'>보안 인증번호 안내</p>"
                    + "</div>"
                    + "<div style='background-color:#121216; padding:24px; border-radius:8px; border:1px solid #2e2e38; text-align:center;'>"
                    + "<p style='font-size:14px; color:#cbd5e1; margin:0 0 16px;'>아래의 6자리 인증번호를 화면에 입력해 주세요.</p>"
                    + "<div style='font-size:32px; font-weight:800; color:#38bdf8; letter-spacing:8px; padding:12px 0;'>" + code + "</div>"
                    + "<p style='font-size:12px; color:#ef4444; margin:14px 0 0;'>* 인증번호 유효시간은 <strong>3분</strong>입니다.</p>"
                    + "</div>"
                    + "</div>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("인증코드 메일 발송 성공 [{}]: key={}, code={}", type, key, code);
        } catch (MessagingException e) {
            verificationStorage.remove(key);
            log.error("메일 발송 실패: {}", e.getMessage());
            throw new IllegalArgumentException("이메일 발송에 실패했습니다. 메일 주소를 다시 확인해 주세요.");
        }
    }

    private String generateNumericCode() {
        SecureRandom random = new SecureRandom();
        int num = 100000 + random.nextInt(900000);
        return String.valueOf(num);
    }
}