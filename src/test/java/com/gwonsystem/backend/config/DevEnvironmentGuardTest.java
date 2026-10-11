package com.gwonsystem.backend.config;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.*;
import static org.junit.jupiter.api.Assertions.*;

class DevEnvironmentGuardTest {
    static final String REF = "abcdefghijklmnopqrst";
    static final String ROLE = "gwon_dev_app";
    static Map<String,Object> settings() {
        var p = new HashMap<String,Object>();
        p.put("APP_DEPLOYMENT_ENV", "dev");
        p.put("DEV_SUPABASE_PROJECT_REF", REF);
        p.put("DEV_DB_ROLE", ROLE); p.put("DEV_DB_NAME", "postgres");
        p.put("spring.datasource.url", "jdbc:postgresql://db." + REF
            + ".supabase.co:5432/postgres?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory");
        p.put("spring.datasource.username", ROLE); p.put("spring.datasource.password", "synthetic-test-only");
        p.put("supabase.storage.url", "https://" + REF + ".supabase.co");
        p.put("supabase.storage.bucket", "dev-images"); p.put("supabase.storage.secret-key", "sb_secret_synthetic_test_only");
        p.put("app.frontend.url", "https://dev-ui.example.test");
        p.put("app.cors.allowed-origins", "https://dev-ui.example.test");
        p.put("DEV_ALLOWED_FRONTEND_ORIGINS", "https://dev-ui.example.test");
        return p;
    }
    static StandardEnvironment environment(Map<String,Object> props) {
        var env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        env.getPropertySources().addFirst(new MapPropertySource("test",props));
        env.setActiveProfiles("dev"); return env;
    }
    static void apply(StandardEnvironment env) { new DevEnvironmentGuard().postProcessEnvironment(env, new SpringApplication()); }
    @Test void locksSchemaAndDisablesExternalIntegrationsEvenWhenGenericOverridesArePresent() {
        var p=settings(); p.put("spring.jpa.hibernate.ddl-auto","create-drop");
        p.put("spring.datasource.hikari.jdbc-url","jdbc:postgresql://production.invalid/postgres");
        p.put("spring.datasource.hikari.connection-init-sql","DROP TABLE members");
        p.put("app.mail.enabled",true); p.put("app.oauth.enabled",true); p.put("app.places.enabled",true);
        p.put("google.places.api-key","synthetic-test-only");
        var e=environment(p); apply(e);
        assertEquals("validate",e.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("validate",e.getProperty("spring.jpa.properties.hibernate.hbm2ddl.auto"));
        assertEquals("never",e.getProperty("spring.sql.init.mode"));
        assertEquals("",e.getProperty("spring.datasource.hikari.connection-init-sql"));
        assertEquals(p.get("spring.datasource.url"),e.getProperty("spring.datasource.hikari.jdbc-url"));
        assertEquals("false",e.getProperty("app.mail.enabled"));
        assertEquals("false",e.getProperty("app.oauth.enabled"));
        assertEquals("false",e.getProperty("app.places.enabled"));
        assertEquals("",e.getProperty("google.places.api-key"));
        assertTrue(e.getProperty("spring.autoconfigure.exclude").contains("OAuth2ClientAutoConfiguration"));
    }
    @Test void rejectsEveryMissingCoreSettingWithoutLeakingValues() {
        for(String k:settings().keySet()) {
            var p=settings(); p.remove(k);
            var ex=assertThrows(IllegalStateException.class,()->apply(environment(p)),k);
            assertFalse(ex.getMessage().contains("synthetic-test-only"));
        }
    }
    @Test void productionAndAmbiguousTargetsAreRejected() {
        for(String url:List.of(
            "jdbc:postgresql://db."+DevEnvironmentGuard.PRODUCTION_REF+".supabase.co:5432/postgres?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory",
            "jdbc:postgresql://db."+REF+".supabase.co.evil.test/postgres",
            "jdbc:postgresql://localhost/postgres", "jdbc:postgresql://db."+REF+".supabase.co/other",
            "jdbc:postgresql://db."+REF+".supabase.co/postgres?sslmode=require",
            settings().get("spring.datasource.url")+"&socketFactory=evil",
            settings().get("spring.datasource.url")+"&user=postgres")) {
            var p=settings();p.put("spring.datasource.url",url);assertThrows(IllegalStateException.class,()->apply(environment(p)));
        }
        for(String k:List.of("DEV_SUPABASE_PROJECT_REF","supabase.storage.url","spring.datasource.username")) {
            var p=settings();p.put(k,k.equals("supabase.storage.url")?"https://"+DevEnvironmentGuard.PRODUCTION_REF+".supabase.co":DevEnvironmentGuard.PRODUCTION_REF);
            assertThrows(IllegalStateException.class,()->apply(environment(p)));
        }
    }
    @Test void sessionPoolerRequiresProjectSpecificLoginAndRejectsTransactionPort() {
        var p=settings();p.put("DEV_DB_POOLER_HOST","aws-0-region.pooler.supabase.com");
        p.put("spring.datasource.url",p.get("spring.datasource.url").toString().replace("db."+REF+".supabase.co","aws-0-region.pooler.supabase.com"));
        assertThrows(IllegalStateException.class,()->apply(environment(p)));
        p.put("spring.datasource.username",ROLE+"."+REF);apply(environment(p));
        p.put("spring.datasource.url",p.get("spring.datasource.url").toString().replace(":5432",":6543"));
        assertThrows(IllegalStateException.class,()->apply(environment(p)));
    }
    @Test void productionOriginsAndLogoCannotBeAllowlisted() {
        for(String o:List.of("https://gwonsystem.com","https://www.gwonsystem.com","https://GWONSYSTEM.COM","*","https://dev-ui.example.test/path")) {
            var p=settings();p.put("DEV_ALLOWED_FRONTEND_ORIGINS",o);p.put("app.cors.allowed-origins",o);p.put("app.frontend.url",o);
            assertThrows(IllegalStateException.class,()->apply(environment(p)));
        }
        var p=settings();p.put("app.email.logo-url","https://"+DevEnvironmentGuard.PRODUCTION_REF+".supabase.co/storage/v1/object/public/public-images/logo.png");
        assertThrows(IllegalStateException.class,()->apply(environment(p)));
        p.put("app.email.logo-url","https://"+REF+".supabase.co/storage/v1/object/public/dev-images/logo.png");apply(environment(p));
    }
    @Test void mixedProfilesAndMissingDevProfileAreRefusedButProductionIsUnchanged() {
        var e=environment(settings());e.setActiveProfiles("dev","prod");assertThrows(IllegalStateException.class,()->apply(e));
        e.setActiveProfiles("prod");assertThrows(IllegalStateException.class,()->apply(e));
        var production=environment(Map.of("spring.jpa.hibernate.ddl-auto","update"));production.setActiveProfiles("prod");apply(production);
        assertEquals("update",production.getProperty("spring.jpa.hibernate.ddl-auto"));
    }
    @Test void enabledIntegrationsRequireDedicatedSettingsRatherThanGenericCredentials() {
        for(String flag:List.of("DEV_MAIL_ENABLED","DEV_OAUTH_ENABLED","DEV_PLACES_ENABLED")) {
            var p=settings();p.put(flag,"true");p.put("MAIL_USERNAME","synthetic");p.put("GOOGLE_CLIENT_ID","synthetic");
            assertThrows(IllegalStateException.class,()->apply(environment(p)));
        }
        var p=settings();p.put("DEV_PLACES_ENABLED","true");p.put("DEV_GOOGLE_PLACES_API_KEY","synthetic-test-only");
        var e=environment(p);apply(e);assertEquals("true",e.getProperty("app.places.enabled"));
    }
    @Test void rejectsJndiAndJpaSchemaGenerationBypasses() {
        for(String k:List.of("spring.datasource.jndi-name","spring.datasource.hikari.data-source-class-name",
            "spring.datasource.hikari.data-source-properties.sslhostnameverifier",
            "spring.datasource.hikari.data-source-properties.socketFactory",
            "spring.jpa.properties.jakarta.persistence.schema-generation.database.action")) {
            var p=settings();p.put(k,"create");assertThrows(IllegalStateException.class,()->apply(environment(p)));
        }
    }
}
