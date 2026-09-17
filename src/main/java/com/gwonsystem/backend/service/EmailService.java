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

    // 🌟 Supabase Storage 공식 로고 이미지 URL
    private static final String LOGO_IMAGE_URL = "https://zsaqpsohhwygrawphnsg.supabase.co/storage/v1/object/public/public-images/Main_Logo.png";

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

    private String normalizeEmail(String email) {
        return (email == null) ? "" : email.trim().toLowerCase();
    }

    // 1. 회원가입용 인증코드 발송
    public void sendVerificationCode(String toEmail) {
        sendOtpMail(toEmail, "REGISTER", "[G-WON SYSTEM] 본인 확인 이메일 인증번호 안내");
    }

    // 2. 비밀번호 재설정용 인증코드 발송
    public void sendPasswordResetCode(String toEmail) {
        sendOtpMail(toEmail, "PW_RESET", "[G-WON SYSTEM] 비밀번호 재설정 인증번호 안내");
    }

    // 3. 아이디 찾기 완료 안내 메일 전송
    public void sendFoundUsernameEmail(String toEmail, String fullName, String username) {
        String cleanEmail = normalizeEmail(toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(cleanEmail);
            helper.setSubject("[G-WON SYSTEM] 회원님의 계정 아이디 안내");

            String htmlContent = buildEmailLayout(
                    "계정 아이디 조회 안내",
                    "요청하신 계정의 아이디 확인 결과입니다.",
                    "<p style='font-size: 15px; color: #334155; margin: 0 0 16px 0; line-height: 1.6; text-align: center;'>"
                            + "<strong>" + fullName + "</strong> 회원님의 가입 아이디는 아래와 같습니다.</p>"
                            + "<div style='background-color: #f8fafc; border: 1px solid #cbd5e1; border-radius: 8px; padding: 18px 24px; text-align: center; margin: 18px 0;'>"
                            + "  <span style='font-size: 24px; font-weight: 800; color: #0284c7; letter-spacing: 1.5px; font-family: Consolas, monospace;'>"
                            + username
                            + "  </span>"
                            + "</div>"
                            + "<p style='font-size: 13px; color: #64748b; margin: 16px 0 0 0; line-height: 1.5; text-align: center;'>"
                            + "로그인 페이지로 이동하여 위 아이디로 로그인해 주세요."
                            + "</p>"
            );

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("아이디 조회 안내 메일 발송 완료: {}", cleanEmail);
        } catch (MessagingException e) {
            log.error("메일 발송 실패: {}", e.getMessage());
            throw new IllegalArgumentException("이메일 발송에 실패했습니다. 메일 주소를 다시 확인해 주세요.");
        }
    }

    // 4. 인증코드 일치 검증
    public boolean verifyCode(String email, String inputCode, String type) {
        String cleanEmail = normalizeEmail(email);
        String cleanCode = (inputCode == null) ? "" : inputCode.trim();

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
            throw new IllegalArgumentException("인증번호 유효시간(3분)이 초과되었습니다. 다시 발송해 주세요.");
        }

        if (!entry.code.equals(cleanCode)) {
            throw new IllegalArgumentException("인증번호가 일치하지 않습니다. 다시 확인해 주세요.");
        }

        verificationStorage.remove(matchedKey);
        verificationStorage.put(verifiedKey, new VerificationEntry("DONE", LocalDateTime.now().plusMinutes(VERIFIED_HOLD_MINUTES)));
        log.info("인증코드 검증 완료 및 상태 유지 시작: key={}", verifiedKey);
        return true;
    }

    public boolean verifyCode(String email, String inputCode) {
        return verifyCode(email, inputCode, "REGISTER");
    }

    // 5. 최종 비밀번호 변경 시 인증 상태 소모
    public void consumeVerification(String email, String code) {
        String cleanEmail = normalizeEmail(email);
        String verifiedKey = "VERIFIED:" + cleanEmail;
        VerificationEntry verifiedEntry = verificationStorage.get(verifiedKey);

        if (verifiedEntry != null && LocalDateTime.now().isBefore(verifiedEntry.expiresAt)) {
            verificationStorage.remove(verifiedKey);
            log.info("비밀번호 변경을 위해 인증 상태를 소모했습니다: {}", cleanEmail);
            return;
        }

        verifyCode(cleanEmail, code, "PW_RESET");
        verificationStorage.remove(verifiedKey);
    }

    // 6. 이메일 발송 실행부
    private void sendOtpMail(String toEmail, String type, String subject) {
        String cleanEmail = normalizeEmail(toEmail);
        String code = generateNumericCode();
        String key = type + ":" + cleanEmail;
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES);

        verificationStorage.remove("VERIFIED:" + cleanEmail);
        verificationStorage.put(key, new VerificationEntry(code, expiresAt));

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(cleanEmail);
            helper.setSubject(subject);

            String htmlContent = buildEmailLayout(
                    "보안 인증번호 안내",
                    "요청하신 서비스 진행을 위한 인증번호입니다.",
                    "<p style='font-size: 15px; color: #334155; margin: 0 0 14px 0; line-height: 1.6; text-align: center;'>"
                            + "화면의 인증번호 입력란에 아래 <strong>6자리 숫자</strong>를 입력해 주세요."
                            + "</p>"
                            + "<div style='background-color: #f8fafc; border: 2px dashed #0284c7; border-radius: 10px; padding: 22px 20px; text-align: center; margin: 24px 0;'>"
                            + "  <div style='font-size: 38px; font-weight: 800; color: #0284c7; letter-spacing: 12px; font-family: Consolas, monospace; padding-left: 12px;'>"
                            + code
                            + "  </div>"
                            + "</div>"
                            + "<div style='background-color: #fef2f2; border-left: 4px solid #ef4444; padding: 12px 16px; border-radius: 4px; margin-top: 20px;'>"
                            + "  <p style='font-size: 13px; color: #b91c1c; margin: 0; line-height: 1.5;'>"
                            + "    ⚠️ 인증번호는 발송 시점으로부터 <strong>3분간만 유효</strong>합니다.<br/>"
                            + "    본인이 요청하지 않은 경우 계정 정보 유출에 유의해 주세요."
                            + "  </p>"
                            + "</div>"
            );

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("인증코드 메일 발송 성공 [{}]: key={}, code={}", type, key, code);
        } catch (MessagingException e) {
            verificationStorage.remove(key);
            log.error("메일 발송 실패: {}", e.getMessage());
            throw new IllegalArgumentException("이메일 발송에 실패했습니다. 메일 주소를 다시 확인해 주세요.");
        }
    }

    // 🌟 7. 로고 이미지 + 하단 G-WON SYSTEM 타이포그래피가 적용된 공통 이메일 레이아웃 템플릿
    private String buildEmailLayout(String subTitle, String desc, String bodyContent) {
        return "<!DOCTYPE html>"
                + "<html>"
                + "<head>"
                + "<meta charset='UTF-8'/>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0'/>"
                + "</head>"
                + "<body style='margin: 0; padding: 40px 16px; background-color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, \"Pretendard\", \"Segoe UI\", Roboto, sans-serif;'>"
                + "  <table align='center' border='0' cellpadding='0' cellspacing='0' width='100%' style='max-width: 520px; background-color: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0; box-shadow: 0 4px 16px rgba(0,0,0,0.06); overflow: hidden;'>"
                + "    <!-- 헤더 브랜드 영역 (로고 이미지 + G-WON SYSTEM 텍스트 결합) -->"
                + "    <tr>"
                + "      <td style='padding: 36px 32px 24px; text-align: center; border-bottom: 1px solid #f1f5f9;'>"
                + "        <div style='text-align: center; margin: 0 auto 14px;'>"
                + "          <img src='" + LOGO_IMAGE_URL + "' alt='G-WON SYSTEM' style='height: 48px; width: auto; max-width: 200px; display: block; margin: 0 auto; border: 0;' />"
                + "          <span style='display: block; font-size: 19px; font-weight: 900; letter-spacing: 0.5px; color: #0f172a; margin-top: 10px; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", sans-serif;'>"
                + "            G-WON SYSTEM"
                + "          </span>"
                + "          <span style='display: block; font-size: 11px; font-weight: 600; color: #ea580c; letter-spacing: 0.5px; margin-top: 2px;'>"
                + "            지원시스템 포털"
                + "          </span>"
                + "        </div>"
                + "        <h2 style='font-size: 17px; font-weight: 700; color: #1e293b; margin: 16px 0 4px;'>" + subTitle + "</h2>"
                + "        <p style='font-size: 13px; color: #64748b; margin: 0;'>" + desc + "</p>"
                + "      </td>"
                + "    </tr>"
                + "    <!-- 메인 본문 영역 -->"
                + "    <tr>"
                + "      <td style='padding: 32px 32px 28px;'>"
                + bodyContent
                + "      </td>"
                + "    </tr>"
                + "    <!-- 푸터 영역 -->"
                + "    <tr>"
                + "      <td style='padding: 24px 32px; background-color: #f8fafc; border-top: 1px solid #f1f5f9; text-align: center; font-size: 12px; color: #94a3b8; line-height: 1.6;'>"
                + "        본 메일은 발신 전용 메일이며 회신되지 않습니다.<br/>"
                + "        © <strong>GWON System Inc.</strong> All Rights Reserved."
                + "      </td>"
                + "    </tr>"
                + "  </table>"
                + "</body>"
                + "</html>";
    }

    private String generateNumericCode() {
        SecureRandom random = new SecureRandom();
        int num = 100000 + random.nextInt(900000);
        return String.valueOf(num);
    }
}