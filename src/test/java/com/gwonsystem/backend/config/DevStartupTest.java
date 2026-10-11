package com.gwonsystem.backend.config;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real SpringApplication/config-data/factories lifecycle, without any real DB connection. */
class DevStartupTest {
    static final AtomicInteger creations = new AtomicInteger();
    @Configuration(proxyBeanMethods=false)
    @Import(CompanyEventSchemaConfig.class)
    static class ProbeConfig {
        @Bean JdbcTemplate jdbcTemplate() { creations.incrementAndGet(); return mock(JdbcTemplate.class); }
    }
    @Configuration(proxyBeanMethods=false)
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration(exclude={
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
        org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class})
    @Import({com.gwonsystem.backend.config.SecurityConfig.class,
        com.gwonsystem.backend.controller.HealthController.class,
        com.gwonsystem.backend.service.EmailService.class, CompanyEventSchemaConfig.class})
    static class WebProbeConfig {
        @Bean com.gwonsystem.backend.security.OAuth2SuccessHandler oauthHandler() {
            return mock(com.gwonsystem.backend.security.OAuth2SuccessHandler.class);
        }
    }
    SpringApplication application(Map<String,Object> settings) {
        var e=DevEnvironmentGuardTest.environment(settings);
        var a=new SpringApplication(ProbeConfig.class);a.setEnvironment(e);a.setWebApplicationType(WebApplicationType.NONE);
        a.setLogStartupInfo(false);a.setRegisterShutdownHook(false);
        a.setDefaultProperties(Map.of("logging.level.root","OFF","spring.main.banner-mode","off"));return a;
    }
    @Test void productionStorageStopsBootBeforeAnyDatasourceBeanIsCreated() {
        var p=DevEnvironmentGuardTest.settings();p.put("supabase.storage.url","https://"+DevEnvironmentGuard.PRODUCTION_REF+".supabase.co");
        creations.set(0);assertThrows(IllegalStateException.class,()->application(p).run());assertEquals(0,creations.get());
    }
    @Test void productionDatabaseStopsBootBeforeAnyDatasourceBeanIsCreated() {
        var p=DevEnvironmentGuardTest.settings();p.put("spring.datasource.url","jdbc:postgresql://db."+DevEnvironmentGuard.PRODUCTION_REF+".supabase.co/postgres");
        creations.set(0);assertThrows(IllegalStateException.class,()->application(p).run());assertEquals(0,creations.get());
    }
    @Test void devLoadsValidateConfigAndNeverRegistersTheLegacyDdlRunner() {
        creations.set(0);
        try(var c=application(DevEnvironmentGuardTest.settings()).run()) {
            assertEquals("validate",c.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
            assertEquals("never",c.getEnvironment().getProperty("spring.sql.init.mode"));
            assertEquals("",c.getEnvironment().getProperty("app.email.logo-url"));
            assertTrue(c.getBeansOfType(ApplicationRunner.class).isEmpty());
            verify(c.getBean(JdbcTemplate.class),never()).execute(anyString());
        }
    }
    @Test void productionNeverRegistersADdlRunner() {
        try(var c=new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            c.getEnvironment().setActiveProfiles("prod");c.register(ProbeConfig.class);c.refresh();
            assertTrue(c.getBeansOfType(ApplicationRunner.class).isEmpty());
            verify(c.getBean(JdbcTemplate.class),never()).execute(anyString());
        } catch(Exception e) { throw new AssertionError(e); }
    }
    @Test void devServletBootNeedsNoSmtpOrOauthCredentialsAndExposesOnlySafeHealth() throws Exception {
        var p=DevEnvironmentGuardTest.settings();p.put("server.port",0);p.put("server.address","127.0.0.1");
        var a=new SpringApplication(WebProbeConfig.class);a.setEnvironment(DevEnvironmentGuardTest.environment(p));
        a.setRegisterShutdownHook(false);a.setDefaultProperties(Map.of("logging.level.root","OFF","spring.main.banner-mode","off"));
        try(var c=a.run()) {
            assertTrue(c.getBeansOfType(org.springframework.security.oauth2.client.registration.ClientRegistrationRepository.class).isEmpty());
            var sender=(org.springframework.mail.javamail.JavaMailSenderImpl)c.getBean(org.springframework.mail.javamail.JavaMailSender.class);
            assertEquals("127.0.0.1",sender.getHost());
            var mail=c.getBean(com.gwonsystem.backend.service.EmailService.class);
            assertThrows(com.gwonsystem.backend.exception.IntegrationDisabledException.class,()->mail.sendVerificationCode("test@example.test"));
            var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(
                (org.springframework.web.context.WebApplicationContext)c)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/internal/health"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/oauth2/authorization/google"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        }
    }
}
