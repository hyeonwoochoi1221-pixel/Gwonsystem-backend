package com.gwonsystem.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gwonsystem.backend.entity.Member;
import com.gwonsystem.backend.repository.MemberRepository;
import com.gwonsystem.backend.service.MemberService;
import com.gwonsystem.backend.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Optional;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityRegressionTest {
    @Test void passwordNeverSerialized() throws Exception {
        Member member = new Member(); member.setPassword("sensitive-hash");
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(member);
        assertFalse(json.contains("password")); assertFalse(json.contains("sensitive-hash"));
    }
    @Test void storedHashCannotBeUsedAsPassword() {
        MemberRepository repository = mock(MemberRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        Member member = new Member(); member.setPassword(encoder.encode("StrongPassword123!"));
        when(repository.findByUsername("alice")).thenReturn(Optional.of(member));
        MemberService service = new MemberService(repository, encoder, mock(EmailService.class));
        assertThrows(IllegalArgumentException.class, () -> service.login("alice", member.getPassword()));
        assertThrows(IllegalArgumentException.class, () -> service.verifyPassword("alice", member.getPassword()));
        assertTrue(service.verifyPassword("alice", "StrongPassword123!"));
    }
    @Test void profileUsesAuthenticatedIdentity() {
        MemberService service = mock(MemberService.class);
        Member member = new Member(); member.setUsername("alice");
        when(service.findByUsername("alice")).thenReturn(member);
        var controller = new com.gwonsystem.backend.controller.MemberController(service);
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("alice", null);
        assertSame(member, controller.getMemberProfile(authentication).getBody());
        verify(service).findByUsername("alice");
    }
    @Test void profileUpdateRequiresRecentPasswordVerification() {
        MemberService service = mock(MemberService.class);
        var controller = new com.gwonsystem.backend.controller.MemberController(service);
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("alice", null);
        var session = new org.springframework.mock.web.MockHttpSession();
        var request = new com.gwonsystem.backend.dto.MemberAdminCreateRequest();
        assertEquals(403, controller.updateMemberProfile(authentication, session, request).getStatusCode().value());
        session.setAttribute("profileVerifiedAt", System.currentTimeMillis() - 300001);
        assertEquals(403, controller.updateMemberProfile(authentication, session, request).getStatusCode().value());
        verifyNoInteractions(service);
    }
    private EmailService emails() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        return new EmailService(sender);
    }
    @Test void resetRequiresCodeEvenAfterFailedVerification() {
        EmailService service = emails(); service.sendPasswordResetCode("alice@example.com");
        assertThrows(IllegalArgumentException.class, () -> service.verifyCode("alice@example.com", "wrong", "PW_RESET"));
        assertThrows(IllegalArgumentException.class, () -> service.consumeVerification("alice@example.com", ""));
    }
    @Test void registrationCodeCannotAuthorizePasswordReset() {
        EmailService service = emails(); service.sendVerificationCode("alice@example.com");
        assertThrows(IllegalArgumentException.class, () -> service.consumeVerification("alice@example.com", "123456"));
    }
    @Test void sendingCodeAgainImmediatelyIsRejected() {
        EmailService service = emails(); service.sendVerificationCode("alice@example.com");
        assertThrows(IllegalArgumentException.class, () -> service.sendVerificationCode("alice@example.com"));
    }
}
