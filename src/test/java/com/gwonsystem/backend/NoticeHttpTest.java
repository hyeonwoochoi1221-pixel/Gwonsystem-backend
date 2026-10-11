package com.gwonsystem.backend;

import com.gwonsystem.backend.config.SecurityConfig;
import com.gwonsystem.backend.controller.NoticeController;
import com.gwonsystem.backend.security.OAuth2SuccessHandler;
import com.gwonsystem.backend.service.NoticeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@WebMvcTest(NoticeController.class)
@Import(SecurityConfig.class)
class NoticeHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean NoticeService notices;
    @MockitoBean OAuth2SuccessHandler oauth;
    @MockitoBean ClientRegistrationRepository registrations;
    @Test void noticeAuthenticationFailuresHaveSafeHttpResponses() throws Exception {
        var devMvc=org.springframework.test.web.servlet.setup.MockMvcBuilders
            .standaloneSetup(new NoticeController(notices))
            .setControllerAdvice(new com.gwonsystem.backend.exception.NoticeAuthExceptionHandler()).build();
        String body="{\"title\":\"Test\",\"content\":\"Test\",\"category\":\"Test\"}";
        when(notices.createNotice(any(),any())).thenThrow(
            new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("internal detail"));
        devMvc.perform(post("/api/notices").contentType("application/json").content(body))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
        doThrow(new org.springframework.security.access.AccessDeniedException("internal detail")).when(notices).createNotice(any(),any());
        devMvc.perform(post("/api/notices").contentType("application/json").content(body))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("권한이 없습니다."));
        doThrow(new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("internal detail"))
            .when(notices).deleteNotice(anyLong(),any());
        devMvc.perform(delete("/api/notices/1")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
        doThrow(new org.springframework.security.access.AccessDeniedException("internal detail"))
            .when(notices).deleteNotice(anyLong(),any());
        devMvc.perform(delete("/api/notices/1")).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("권한이 없습니다."));
    }
    @Test void createKeepsRoleCsrfResponseAndPassesTheAuthenticatedPrincipal() throws Exception {
        String body="{\"title\":\"Test\",\"content\":\"Test\",\"category\":\"Test\",\"authorId\":999}";
        mvc.perform(post("/api/notices").with(csrf()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/notices").with(csrf()).with(user("associate").roles("ASSOCIATE")).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/notices").with(user("admin").roles("SUPERVISOR")).contentType("application/json").content(body)).andExpect(status().isForbidden());
        verifyNoInteractions(notices);
        when(notices.createNotice(any(),any())).thenReturn(77L);
        for(String role:List.of("REGULAR","SUPERVISOR")) {
            mvc.perform(post("/api/notices").with(csrf()).with(user("writer").roles(role)).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(content().string("77"));
        }
        verify(notices,times(2)).createNotice(any(),argThat(auth->"writer".equals(auth.getName())));
    }
    @Test void publicListAndAuthenticatedContentPermissionsArePreserved() throws Exception {
        when(notices.getAllNotices()).thenReturn(List.of());mvc.perform(get("/api/notices")).andExpect(status().isOk());
        clearInvocations(notices);
        mvc.perform(delete("/api/notices/1").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/notices/1").with(csrf()).with(user("associate").roles("ASSOCIATE"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/notices/1").with(user("regular").roles("REGULAR"))).andExpect(status().isForbidden());
        verifyNoInteractions(notices);
        mvc.perform(delete("/api/notices/1").with(csrf()).with(user("regular").roles("REGULAR"))).andExpect(status().isNoContent());
        verify(notices).deleteNotice(eq(1L),argThat(auth->"regular".equals(auth.getName())));
        mvc.perform(delete("/api/notices/1").with(csrf()).with(user("admin").roles("SUPERVISOR"))).andExpect(status().isNoContent());
        verify(notices).deleteNotice(eq(1L),argThat(auth->"admin".equals(auth.getName())));
    }
}
