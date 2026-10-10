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
    @Test void publicListAndAuthenticatedContentPermissionsArePreserved() throws Exception {
        when(notices.getAllNotices()).thenReturn(List.of());mvc.perform(get("/api/notices")).andExpect(status().isOk());
        clearInvocations(notices);
        mvc.perform(delete("/api/notices/1").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/notices/1").with(csrf()).with(user("associate").roles("ASSOCIATE"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/notices/1").with(user("regular").roles("REGULAR"))).andExpect(status().isForbidden());
        verifyNoInteractions(notices);
        mvc.perform(delete("/api/notices/1").with(csrf()).with(user("regular").roles("REGULAR"))).andExpect(status().isNoContent());
        verify(notices).deleteNotice(1L);
    }
}
