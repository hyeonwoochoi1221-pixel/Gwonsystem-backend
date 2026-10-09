package com.gwonsystem.backend;

import com.gwonsystem.backend.config.SecurityConfig;
import com.gwonsystem.backend.controller.CompanyEventController;

import com.gwonsystem.backend.security.OAuth2SuccessHandler;
import com.gwonsystem.backend.repository.CompanyEventRepository;
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

@WebMvcTest(CompanyEventController.class)
@Import(SecurityConfig.class)
class CompanyEventHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean CompanyEventRepository events;
    @MockitoBean com.gwonsystem.backend.service.GooglePlacesService places;
    @MockitoBean OAuth2SuccessHandler oauth;
    @MockitoBean ClientRegistrationRepository registrations;
    String valid = "{\"title\":\"회의\",\"category\":\"회의\",\"startDate\":\"2026-10-10\",\"endDate\":\"2026-10-11\",\"location\":\"\",\"description\":\"\"}";
    @Test void publicCanReadEvents() throws Exception {
        when(events.findAllByOrderByStartDateDescIdDesc()).thenReturn(List.of());
        mvc.perform(get("/api/company-events")).andExpect(status().isOk());
    }
    @Test void onlySupervisorCanCreateWithCsrf() throws Exception {
        mvc.perform(post("/api/company-events").contentType("application/json").content(valid).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/company-events").contentType("application/json").content(valid).with(csrf()).with(user("member").roles("REGULAR"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/company-events").contentType("application/json").content(valid).with(user("admin").roles("SUPERVISOR"))).andExpect(status().isForbidden());
        verifyNoInteractions(events);
        when(events.save(any())).thenAnswer(i -> i.getArgument(0));
        mvc.perform(post("/api/company-events").contentType("application/json").content(valid).with(csrf()).with(user("admin").roles("SUPERVISOR"))).andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("회의"));
    }
    @Test void invalidDateRangeAndBlankTitleAreRejected() throws Exception {
        mvc.perform(post("/api/company-events").contentType("application/json").content(valid.replace("2026-10-11","2026-10-09")).with(csrf()).with(user("admin").roles("SUPERVISOR"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/company-events").contentType("application/json").content(valid.replace("\"title\":\"회의\"","\"title\":\" \" ")).with(csrf()).with(user("admin").roles("SUPERVISOR"))).andExpect(status().isBadRequest());
        verifyNoInteractions(events);
    }
    @Test void openEndedEventAndLocationDetailsAreSaved() throws Exception {
        when(events.save(any())).thenAnswer(i -> i.getArgument(0));
        String input = valid.replace("\"endDate\":\"2026-10-11\"", "\"endDate\":null").replace("\"description\":\"\"", "\"description\":\"\",\"locationDetail\":\"202호\",\"placeId\":\"test-place\"");
        mvc.perform(post("/api/company-events").contentType("application/json").content(input).with(csrf()).with(user("admin").roles("SUPERVISOR")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.endDate").isEmpty()).andExpect(jsonPath("$.locationDetail").value("202호"));
    }
    @Test void placesSearchRequiresSupervisor() throws Exception {
        mvc.perform(get("/api/company-events/places").param("query","서울역").with(user("member").roles("REGULAR"))).andExpect(status().isForbidden());
        verifyNoInteractions(places);
        when(places.search("서울역")).thenReturn(List.of(new com.gwonsystem.backend.service.GooglePlacesService.Place("test", "서울역", "서울")));
        mvc.perform(get("/api/company-events/places").param("query","서울역").with(user("admin").roles("SUPERVISOR"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("서울역"));
    }
}
