package com.gwonsystem.backend;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import com.gwonsystem.backend.service.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class DevIntegrationsTest {
    @Test void disabledMailNeverSendsOrStoresOtp() {
        var sender=mock(JavaMailSender.class);var service=new EmailService(sender);
        ReflectionTestUtils.setField(service,"mailEnabled",false);
        assertThrows(ResponseStatusException.class,()->service.sendVerificationCode("test@example.test"));
        assertThrows(ResponseStatusException.class,()->service.sendPasswordResetCode("test@example.test"));
        assertThrows(ResponseStatusException.class,()->service.sendFoundUsernameEmail("test@example.test","Test","test"));
        verifyNoInteractions(sender);
        assertThrows(IllegalArgumentException.class,()->service.verifyCode("test@example.test","000000"));
    }
    @Test void mailRejectsRecipientsOutsideTheExactAllowlist() {
        var sender=mock(JavaMailSender.class);var service=new EmailService(sender);
        ReflectionTestUtils.setField(service,"allowedRecipients","approved@example.test");
        assertThrows(ResponseStatusException.class,()->service.sendVerificationCode("other@example.test"));verifyNoInteractions(sender);
    }
    @Test void emptyDevelopmentLogoHasNoImageOrProductionFallback() {
        var service=new EmailService(mock(JavaMailSender.class));
        String html=ReflectionTestUtils.invokeMethod(service,"buildEmailLayout","Test","Test","Test");
        assertFalse(html.contains("<img"));assertFalse(html.contains("supabase.co"));
        ReflectionTestUtils.setField(service,"logoImageUrl","https://dev.example.test/logo.png");
        html=ReflectionTestUtils.invokeMethod(service,"buildEmailLayout","Test","Test","Test");
        assertTrue(html.contains("https://dev.example.test/logo.png"));
    }
    @Test void disabledPlacesDoesNotPerformNetworkCalls() {
        var service=new GooglePlacesService(RestClient.builder(),"synthetic-test-only");
        ReflectionTestUtils.setField(service,"enabled",false);
        assertThrows(ResponseStatusException.class,()->service.search("서울역"));
    }
}
