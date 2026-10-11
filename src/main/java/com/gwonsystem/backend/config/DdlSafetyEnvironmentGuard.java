package com.gwonsystem.backend.config;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Runs before datasource creation; messages never include configuration values. */
public final class DdlSafetyEnvironmentGuard implements EnvironmentPostProcessor, Ordered {
    @Override public int getOrder() { return ConfigDataEnvironmentPostProcessor.ORDER + 2; }

    @Override public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        // DevEnvironmentGuard already locks these settings and verifies its separate trust boundary.
        if (java.util.Arrays.asList(env.getActiveProfiles()).contains("dev")) return;
        var binder = Binder.get(env);
        var locked = new java.util.LinkedHashMap<String,Object>();
        for (var entry : Map.of(
                "spring.jpa.hibernate.ddl-auto", "validate",
                "spring.jpa.generate-ddl", "false",
                "spring.sql.init.mode", "never",
                "spring.flyway.enabled", "false",
                "spring.liquibase.enabled", "false",
                "spring.datasource.hikari.connection-init-sql", "").entrySet()) {
            var value = binder.bind(entry.getKey(), String.class).orElse(entry.getValue());
            if (!entry.getValue().equalsIgnoreCase(value.trim())) reject(entry.getKey());
            locked.put(entry.getKey(), entry.getValue());
        }
        var nativeProperties = binder.bind("spring.jpa.properties",
            org.springframework.boot.context.properties.bind.Bindable.mapOf(String.class, String.class))
            .orElse(Map.of());
        for (String key : new String[]{"hibernate.hbm2ddl.auto",
                "jakarta.persistence.schema-generation.database.action",
                "javax.persistence.schema-generation.database.action",
                "jakarta.persistence.schema-generation.scripts.action",
                "javax.persistence.schema-generation.scripts.action"}) {
            String value = nativeProperties.get(key);
            if (value == null) value = env.getProperty("spring.jpa.properties." + key);
            if (value == null) value = env.getProperty(key);
            if (value != null && !value.isBlank()
                    && !(key.equals("hibernate.hbm2ddl.auto") && value.equalsIgnoreCase("validate")))
                reject("spring.jpa.properties." + key);
        }
        locked.put("spring.jpa.properties.hibernate.hbm2ddl.auto", "validate");
        env.getPropertySources().addFirst(new MapPropertySource("ddlSafety", locked));
    }

    private static void reject(String key) {
        throw new IllegalStateException("Automatic schema initialization is prohibited: " + key);
    }
}
