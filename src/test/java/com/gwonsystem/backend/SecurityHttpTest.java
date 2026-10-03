package com.gwonsystem.backend;

import com.gwonsystem.backend.config.SecurityConfig;
import com.gwonsystem.backend.controller.MemberController;
import com.gwonsystem.backend.dto.LoginResponse;
import com.gwonsystem.backend.entity.Member;
import com.gwonsystem.backend.entity.Role;
import com.gwonsystem.backend.security.OAuth2SuccessHandler;
import com.gwonsystem.backend.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MemberController.class)
@Import(SecurityConfig.class)
class SecurityHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean MemberService members;
    @MockitoBean OAuth2SuccessHandler oauth;
    @MockitoBean ClientRegistrationRepository registrations;

    @Test void anonymousProfileAndMemberListAreDenied() throws Exception {
        mvc.perform(get("/api/members/profile").param("username", "victim")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/members")).andExpect(status().isUnauthorized());
        verifyNoInteractions(members);
    }
    @Test void associateCannotAccessAdministration() throws Exception {
        mvc.perform(get("/api/members").with(user("alice").roles("ASSOCIATE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/users").with(user("alice").roles("ASSOCIATE"))).andExpect(status().isForbidden());
    }
    @Test void mutationWithoutCsrfIsDenied() throws Exception {
        mvc.perform(put("/api/members/profile").with(user("alice")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/members/login").contentType("application/json").content("{\"username\":\"alice\",\"password\":\"password\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(members);
    }
    @Test void suppliedUsernameCannotSelectAnotherProfile() throws Exception {
        Member member = new Member(); member.setUsername("alice"); member.setPassword("hidden-hash");
        when(members.findByUsername("alice")).thenReturn(member);
        mvc.perform(get("/api/members/profile").param("username", "victim").with(user("alice")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice")).andExpect(jsonPath("$.password").doesNotExist());
        verify(members).findByUsername("alice"); verify(members, never()).findByUsername("victim");
    }
    @Test void csrfEndpointProvidesUsableTokenAndLoginEstablishesSession() throws Exception {
        var csrfResult = mvc.perform(get("/api/members/csrf")).andExpect(status().isOk()).andReturn();
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(csrfResult.getResponse().getContentAsString());
        MockHttpSession previous = (MockHttpSession) csrfResult.getRequest().getSession();
        when(members.login("alice", "password")).thenReturn(LoginResponse.builder().username("alice").role("ROLE_ASSOCIATE").build());
        var login = mvc.perform(post("/api/members/login").session(previous)
            .header(json.get("headerName").asText(), json.get("token").asText())
            .contentType("application/json").content("{\"username\":\"alice\",\"password\":\"password\"}"))
            .andExpect(status().isOk()).andReturn();
        MockHttpSession authenticated = (MockHttpSession) login.getRequest().getSession();
        org.junit.jupiter.api.Assertions.assertNotEquals(previous.getId(), authenticated.getId());
        Member member = new Member(); member.setUsername("alice"); member.setRole(Role.ROLE_ASSOCIATE);
        when(members.findByUsername("alice")).thenReturn(member);
        mvc.perform(get("/api/members/me").session(authenticated)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice"));
        mvc.perform(post("/api/members/logout").session(authenticated).with(csrf())).andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertTrue(authenticated.isInvalid());
    }
    @Test void untrustedVercelOriginIsDenied() throws Exception {
        mvc.perform(options("/api/members/csrf").header("Origin", "https://attacker.vercel.app").header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden());
    }
}
