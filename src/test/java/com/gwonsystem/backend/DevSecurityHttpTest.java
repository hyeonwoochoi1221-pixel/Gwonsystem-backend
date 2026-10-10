package com.gwonsystem.backend;

import com.gwonsystem.backend.config.SecurityConfig;
import com.gwonsystem.backend.controller.*;
import com.gwonsystem.backend.service.*;
import com.gwonsystem.backend.security.OAuth2SuccessHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

// Security slice: uses dev integration values without live DB/Supabase configuration.
@WebMvcTest(value={MemberController.class,EmailController.class,CompanyEventController.class},properties={
    "app.oauth.enabled=false", "app.cors.allowed-origins=https://dev-ui.example.test"})
@Import(SecurityConfig.class)
class DevSecurityHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean MemberService members;
    @MockitoBean EmailService mail;
    @MockitoBean com.gwonsystem.backend.repository.CompanyEventRepository events;
    @MockitoBean GooglePlacesService places;
    @MockitoBean OAuth2SuccessHandler oauth;
    @Test void disabledOauthRoutesCannotInitiateLogin() throws Exception {
        mvc.perform(get("/oauth2/authorization/google")).andExpect(status().isUnauthorized());
        mvc.perform(get("/login/oauth2/code/naver")).andExpect(status().isUnauthorized());verifyNoInteractions(oauth);
    }
    @Test void developmentOriginWorksAndProductionOriginIsDenied() throws Exception {
        mvc.perform(options("/api/members/csrf").header("Origin","https://dev-ui.example.test").header("Access-Control-Request-Method","GET"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","https://dev-ui.example.test"));
        mvc.perform(options("/api/members/csrf").header("Origin","https://gwonsystem.com").header("Access-Control-Request-Method","GET"))
            .andExpect(status().isForbidden());
    }
    @Test void disabledMailReturns503ThroughTheRealControllerAndAdvice() throws Exception {
        doThrow(new com.gwonsystem.backend.exception.IntegrationDisabledException("disabled")).when(mail).sendVerificationCode(anyString());
        mvc.perform(post("/api/members/email/send-code").with(csrf()).contentType("application/json").content("{\"email\":\"test@example.test\"}"))
            .andExpect(status().isServiceUnavailable());
    }
    @Test void disabledPlacesReturns503ForAuthorizedAdministrator() throws Exception {
        when(places.search(anyString())).thenThrow(new com.gwonsystem.backend.exception.IntegrationDisabledException("disabled"));
        mvc.perform(get("/api/company-events/places").param("query","서울역").with(user("admin").roles("SUPERVISOR")))
            .andExpect(status().isServiceUnavailable());
    }
}
