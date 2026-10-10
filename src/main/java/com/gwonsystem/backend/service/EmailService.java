// src/main/java/com/gwonsystem/backend/service/EmailService.java
package com.gwonsystem.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import com.gwonsystem.backend.exception.IntegrationDisabledException;

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
    @Value("${app.email.logo-url:}")
    private String logoImageUrl = "";
    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled = true;
    @Value("${app.mail.allowed-recipients:}")
    private String allowedRecipients = "";

    private void requireMail(String recipient) {
        if (!mailEnabled) throw new IntegrationDisabledException("이메일 발송 기능이 비활성화되어 있습니다.");
        if (!allowedRecipients.isBlank() && java.util.Arrays.stream(allowedRecipients.split(","))
            .map(this::normalizeEmail).noneMatch(normalizeEmail(recipient)::equals))
            throw new IntegrationDisabledException("개발 환경에서는 승인된 테스트 이메일로만 발송할 수 있습니다.");
    }

    // 인증코드 및 인증완료 상태 저장소
    private final Map<String, VerificationEntry> verificationStorage = new ConcurrentHashMap<>();
    private static final int EXPIRATION_MINUTES = 3;
    private static final int VERIFIED_HOLD_MINUTES = 10;

    private static class VerificationEntry {
        String code;
        int attempts;
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
        requireMail(toEmail);
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
    public synchronized boolean verifyCode(String email, String inputCode, String type) {
        String key = type + ":" + normalizeEmail(email);
        VerificationEntry entry = verificationStorage.get(key);
        if (entry == null || LocalDateTime.now().isAfter(entry.expiresAt)) {
            verificationStorage.remove(key);
            throw new IllegalArgumentException("인증번호가 만료되었습니다.");
        }
        if (++entry.attempts > 5) {
            verificationStorage.remove(key);
            throw new IllegalArgumentException("인증 시도 횟수를 초과했습니다.");
        }
        if (!entry.code.equals(inputCode == null ? "" : inputCode.trim()))
            throw new IllegalArgumentException("인증번호가 일치하지 않습니다.");
        return true;
    }
    public boolean verifyCode(String email, String code) { return verifyCode(email, code, "REGISTER"); }
    public synchronized void consumeVerification(String email, String code) {
        verifyCode(email, code, "PW_RESET");
        verificationStorage.remove("PW_RESET:" + normalizeEmail(email));
    }
    public synchronized void consumeRegistration(String email, String code) {
        verifyCode(email, code, "REGISTER");
        verificationStorage.remove("REGISTER:" + normalizeEmail(email));
    }

    // 6. 이메일 발송 실행부
    private synchronized void sendOtpMail(String toEmail, String type, String subject) {
        requireMail(toEmail);
        String cleanEmail = normalizeEmail(toEmail);
        verificationStorage.entrySet().removeIf(e -> LocalDateTime.now().isAfter(e.getValue().expiresAt));
        VerificationEntry previous = verificationStorage.get(type + ":" + cleanEmail);
        if (previous != null && previous.expiresAt.minusMinutes(EXPIRATION_MINUTES).plusSeconds(60).isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("인증번호 재발송은 60초 후 가능합니다.");
        if (verificationStorage.size() >= 10000) throw new IllegalArgumentException("잠시 후 다시 시도해 주세요.");
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
            log.info("인증코드 메일 발송 성공 [{}]", type);
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
                + (logoImageUrl.isBlank() ? "" : "          <img src='" + org.springframework.web.util.HtmlUtils.htmlEscape(logoImageUrl) + "' alt='G-WON SYSTEM' style='height: 48px; width: auto; max-width: 200px; display: block; margin: 0 auto; border: 0;' />")
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
