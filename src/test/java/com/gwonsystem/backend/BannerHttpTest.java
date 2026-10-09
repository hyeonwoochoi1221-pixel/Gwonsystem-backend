package com.gwonsystem.backend;

import com.gwonsystem.backend.config.SecurityConfig;
import com.gwonsystem.backend.controller.BannerController;
import com.gwonsystem.backend.dto.BannerResponse;
import com.gwonsystem.backend.security.OAuth2SuccessHandler;
import com.gwonsystem.backend.service.BannerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BannerController.class)
@Import(SecurityConfig.class)
class BannerHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean BannerService gallery;
    @MockitoBean OAuth2SuccessHandler oauth;
    @MockitoBean ClientRegistrationRepository registrations;
    MockMultipartFile file() { return new MockMultipartFile("photo", "photo.png", "image/png", new byte[]{1}); }

    @Test void anonymousVisitorsCanReadMetadataWithoutPhotoBytes() throws Exception {
        when(gallery.list()).thenReturn(List.of(new BannerResponse(1L, "Photo", "Description", Instant.now())));
        mvc.perform(get("/api/banners")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("Photo")).andExpect(jsonPath("$[0].image").doesNotExist());
    }
    @Test void anonymousAndAssociateUploadsAreDenied() throws Exception {
        mvc.perform(multipart("/api/banners").file(file()).param("title", "Photo").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/banners").file(file()).param("title", "Photo").with(csrf()).with(user("associate").roles("ASSOCIATE"))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/banners").file(file()).param("title", "Photo").with(csrf()).with(user("regular").roles("REGULAR"))).andExpect(status().isForbidden());
        verifyNoInteractions(gallery);
    }
    @Test void uploadRequiresCsrfEvenForSupervisor() throws Exception {
        mvc.perform(multipart("/api/banners").file(file()).param("title", "Photo").with(user("admin").roles("SUPERVISOR"))).andExpect(status().isForbidden());
        verifyNoInteractions(gallery);
    }
    @Test void supervisorCanUploadWithAuthenticatedAuthor() throws Exception {
        when(gallery.create(anyString(), anyString(), any(), anyString())).thenReturn(new BannerResponse(1L, "Photo", "", Instant.now()));
        for (String role : List.of("SUPERVISOR")) {
            mvc.perform(multipart("/api/banners").file(file()).param("title", "Photo")
                .with(csrf()).with(user("author").roles(role))).andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/banners/1/image"));
        }
        verify(gallery, times(1)).create(eq("Photo"), eq(""), any(), eq("author"));
    }
    @Test void onlySupervisorCanDeleteWithCsrf() throws Exception {
        mvc.perform(delete("/api/banners/1").with(csrf()).with(user("regular").roles("REGULAR"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/banners/1").with(user("admin").roles("SUPERVISOR"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/banners/1").with(csrf()).with(user("admin").roles("SUPERVISOR"))).andExpect(status().isNoContent());
        verify(gallery).delete(1L);
    }
    @Test void anonymousImageResponseHasCorrectContentType() throws Exception {
        when(gallery.image(1L)).thenReturn(new com.gwonsystem.backend.entity.Banner("Photo", "", "image/png", new byte[]{1, 2}, "author"));
        mvc.perform(get("/api/banners/1/image")).andExpect(status().isOk())
            .andExpect(content().contentType("image/png")).andExpect(content().bytes(new byte[]{1, 2}))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
