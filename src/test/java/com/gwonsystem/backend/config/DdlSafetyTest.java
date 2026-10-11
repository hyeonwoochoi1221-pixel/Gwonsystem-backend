package com.gwonsystem.backend.config;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.*;
import org.springframework.core.env.*;
import static org.junit.jupiter.api.Assertions.*;

class DdlSafetyTest {
    StandardEnvironment environment() {
        var e = new StandardEnvironment();
        e.getPropertySources().remove("systemEnvironment");
        e.getPropertySources().remove("systemProperties");
        return e;
    }
    void apply(StandardEnvironment e) {
        new DdlSafetyEnvironmentGuard().postProcessEnvironment(e, new SpringApplication());
    }
    @Test void defaultAndProdBootUseValidateWithoutDatabaseOrDdlRunner() {
        for (String profile : List.of("", "prod")) {
            var e = environment();
            if (!profile.isEmpty()) e.setActiveProfiles(profile);
            var app = new SpringApplication(DevStartupTest.ProbeConfig.class);
            app.setEnvironment(e); app.setWebApplicationType(WebApplicationType.NONE);
            app.setRegisterShutdownHook(false);
            app.setDefaultProperties(Map.of("logging.level.root","OFF","spring.main.banner-mode","off"));
            try (var c = app.run()) {
                assertEquals("validate", c.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
                assertEquals("validate", c.getEnvironment().getProperty("spring.jpa.properties.hibernate.hbm2ddl.auto"));
                assertEquals("never", c.getEnvironment().getProperty("spring.sql.init.mode"));
                assertTrue(c.getBeansOfType(ApplicationRunner.class).isEmpty());
            }
        }
    }
    @Test void refusesEnvironmentAndCommandLineDdlOverrides() {
        for (String action : List.of("update","create","create-drop","none")) {
            var e = environment();
            e.getPropertySources().addFirst(new SystemEnvironmentPropertySource("env",
                Map.of("SPRING_JPA_HIBERNATE_DDL_AUTO",action)));
            assertThrows(IllegalStateException.class, () -> apply(e));
            var cli = environment();
            cli.getPropertySources().addFirst(new SimpleCommandLinePropertySource(
                "--spring.jpa.hibernate.ddl-auto=" + action));
            assertThrows(IllegalStateException.class, () -> apply(cli));
        }
    }
    @Test void refusesNativeActionsAndSqlInitialization() {
        for (var entry : Map.of(
                "spring.jpa.properties.hibernate.hbm2ddl.auto","update",
                "spring.jpa.properties.jakarta.persistence.schema-generation.database.action","none",
                "spring.jpa.properties.javax.persistence.schema-generation.scripts.action","create",
                "spring.sql.init.mode","always",
                "spring.jpa.generate-ddl","true",
                "spring.flyway.enabled","true",
                "spring.liquibase.enabled","true",
                "spring.datasource.hikari.connection-init-sql","synthetic unsafe value").entrySet()) {
            var e = environment();
            e.getPropertySources().addFirst(new MapPropertySource("test",Map.of(entry.getKey(),entry.getValue())));
            var ex = assertThrows(IllegalStateException.class, () -> apply(e),entry.getKey());
            assertFalse(ex.getMessage().contains(entry.getValue()));
        }
    }
    @Test void devRetainsItsExistingLockedSettings() {
        var p = DevEnvironmentGuardTest.settings();
        p.put("spring.jpa.hibernate.ddl-auto","create-drop");
        var e = DevEnvironmentGuardTest.environment(p);
        DevEnvironmentGuardTest.apply(e); apply(e);
        assertEquals("validate",e.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("never",e.getProperty("spring.sql.init.mode"));
    }
    @Test void nativeEnvironmentAndBracketPropertiesCannotEnableDdl() {
        var e = environment();
        e.getPropertySources().addFirst(new SystemEnvironmentPropertySource("env",
            Map.of("SPRING_JPA_PROPERTIES_HIBERNATE_HBM2DDL_AUTO","update")));
        assertThrows(IllegalStateException.class, () -> apply(e));
        var bracket = environment();
        bracket.getPropertySources().addFirst(new MapPropertySource("test",Map.of(
            "spring.jpa.properties[hibernate.hbm2ddl.auto]","create")));
        assertThrows(IllegalStateException.class, () -> apply(bracket));
    }
    @Test void unsafeCliStopsRealBootBeforeBeans() {
        for (String profile : List.of("", "prod")) {
            var e = environment();
            if (!profile.isEmpty()) e.setActiveProfiles(profile);
            var app = new SpringApplication(DevStartupTest.ProbeConfig.class);
            app.setEnvironment(e); app.setWebApplicationType(WebApplicationType.NONE);
            app.setRegisterShutdownHook(false);
            app.setDefaultProperties(Map.of("logging.level.root","OFF","spring.main.banner-mode","off"));
            DevStartupTest.creations.set(0);
            assertThrows(IllegalStateException.class,
                () -> app.run("--spring.jpa.hibernate.ddl-auto=update"));
            assertEquals(0,DevStartupTest.creations.get());
        }
    }
    @Test void finalHibernateMapIsValidateAndCannotGenerateSchemas() {
        var customizer = new DdlSafetyConfiguration.ValidateOnly();
        var properties = new HashMap<String,Object>();
        customizer.customize(properties);
        assertEquals("validate",properties.get("hibernate.hbm2ddl.auto"));
        properties.put("hibernate.hbm2ddl.auto","update");
        assertThrows(IllegalStateException.class, () -> customizer.customize(properties));
        for (String prefix : List.of("jakarta","javax")) {
            for (String target : List.of("database","scripts")) {
                var actions = new HashMap<String,Object>();
                actions.put(prefix+".persistence.schema-generation."+target+".action","create");
                assertThrows(IllegalStateException.class, () -> customizer.customize(actions));
            }
        }
    }
}
