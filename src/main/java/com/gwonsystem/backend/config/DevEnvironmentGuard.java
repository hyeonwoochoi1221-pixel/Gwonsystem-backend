package com.gwonsystem.backend.config;

import java.net.URI;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.*;

/** Runs after config data, before datasource/OAuth/mail beans or network activity. */
public final class DevEnvironmentGuard implements EnvironmentPostProcessor, Ordered {
    public static final String PRODUCTION_REF = "zsaqpsohhwygrawphnsg";
    private static final String OAUTH_AUTO_CONFIG =
        "org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration";

    @Override public int getOrder() { return ConfigDataEnvironmentPostProcessor.ORDER + 1; }

    @Override public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        boolean dev = Arrays.asList(env.getActiveProfiles()).contains("dev");
        boolean designatedDev = "dev".equals(env.getProperty("APP_DEPLOYMENT_ENV"));
        if (!dev && (designatedDev || env.getProperty("RENDER_SERVICE_NAME", "").endsWith("-dev")))
            fail("SPRING_PROFILES_ACTIVE must be dev for a development deployment");
        if (!dev) return;
        if (!designatedDev || env.getActiveProfiles().length != 1)
            fail("dev requires APP_DEPLOYMENT_ENV=dev and no additional active profiles");

        String ref = required(env, "DEV_SUPABASE_PROJECT_REF");
        if (!ref.matches("[a-z]{20}") || PRODUCTION_REF.equals(ref)) fail("Invalid development project allowlist");
        String role = required(env, "DEV_DB_ROLE");
        if (!role.matches("[a-z][a-z0-9_]{2,62}") || Set.of("postgres", "supabase_admin", "service_role", "anon", "authenticated").contains(role))
            fail("DEV_DB_ROLE must identify a dedicated, non-owner development login role");
        String db = required(env, "DEV_DB_NAME");
        if (!db.matches("[a-zA-Z0-9_]+")) fail("Invalid DEV_DB_NAME");
        validateDatabaseUrl(required(env, "spring.datasource.url"),
            required(env, "spring.datasource.username"), ref, role, db, env.getProperty("DEV_DB_POOLER_HOST", ""));
        required(env, "spring.datasource.password");
        String storage = required(env, "supabase.storage.url");
        if (!storage.equals("https://" + ref + ".supabase.co")) fail("Storage URL is outside the development project");
        if (!required(env, "supabase.storage.bucket").matches("[a-zA-Z0-9_-]{1,100}")) fail("Invalid development storage bucket");
        String key = required(env, "supabase.storage.secret-key");
        validateLegacyStorageKey(key, ref);
        String logo = env.getProperty("app.email.logo-url", "");
        if (!logo.isBlank()) {
            URI uri = uri(logo, "Invalid development email logo URL");
            if (!"https".equals(uri.getScheme()) || !(ref + ".supabase.co").equals(uri.getHost())
                || uri.getPort() != -1 || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !uri.getPath().startsWith("/storage/v1/object/public/" + required(env, "supabase.storage.bucket") + "/"))
                fail("Email logo must use the development Storage bucket or be empty");
        }
        String frontend = origin(required(env, "app.frontend.url"));
        Set<String> approved = origins(required(env, "DEV_ALLOWED_FRONTEND_ORIGINS"));
        Set<String> actual = origins(required(env, "app.cors.allowed-origins"));
        if (!approved.contains(frontend) || !approved.containsAll(actual) || !actual.contains(frontend))
            fail("Frontend/CORS origins are outside the development allowlist");

        Map<String, Object> locked = new HashMap<>();
        locked.put("spring.jpa.hibernate.ddl-auto", "validate");
        locked.put("spring.jpa.properties.hibernate.hbm2ddl.auto", "validate");
        rejectIfPresent(env, "spring.jpa.properties.jakarta.persistence.schema-generation.database.action",
            "spring.jpa.properties.javax.persistence.schema-generation.database.action",
            "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action");
        locked.put("spring.jpa.generate-ddl", "false");
        locked.put("spring.sql.init.mode", "never");
        locked.put("spring.datasource.hikari.connection-init-sql", "");
        locked.put("spring.datasource.hikari.data-source-properties.sslmode", "verify-full");
        locked.put("spring.datasource.hikari.data-source-properties.sslfactory", "org.postgresql.ssl.DefaultJavaSSLFactory");
        // Put temporary relations after public; pg_catalog is implicitly searched first.
        locked.put("spring.datasource.hikari.data-source-properties.currentSchema", "public,pg_temp");
        locked.put("spring.jpa.properties.hibernate.default_schema", "public");
        locked.put("spring.flyway.enabled", "false");
        locked.put("spring.liquibase.enabled", "false");
        locked.put("server.servlet.session.cookie.secure", "true");
        locked.put("server.servlet.session.cookie.same-site", "none");
        locked.put("server.servlet.session.cookie.domain", "");
        locked.put("server.forward-headers-strategy", "framework");
        // Prevent alternate DataSource factories/properties bypassing URL validation.
        rejectIfPresent(env, "spring.datasource.jndi-name", "spring.datasource.hikari.data-source-class-name",
            "spring.datasource.hikari.data-source-j-n-d-i", "spring.datasource.hikari.data-source-properties.user",
            "spring.datasource.hikari.data-source-properties.password", "spring.datasource.hikari.data-source-properties.serverName",
            "spring.datasource.hikari.data-source-properties.databaseName", "spring.datasource.hikari.data-source-properties.socketFactory",
            "spring.datasource.hikari.data-source-properties.sslhostnameverifier", "spring.datasource.hikari.data-source-properties.options",
            "spring.mail.jndi-name");
        locked.put("spring.datasource.type", "com.zaxxer.hikari.HikariDataSource");
        locked.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        locked.put("spring.datasource.hikari.jdbc-url", required(env, "spring.datasource.url"));
        locked.put("spring.datasource.hikari.username", required(env, "spring.datasource.username"));
        locked.put("spring.datasource.hikari.password", required(env, "spring.datasource.password"));

        boolean mail = enabled(env, "DEV_MAIL_ENABLED");
        locked.put("app.mail.enabled", mail);
        if (mail) {
            locked.put("spring.mail.host", required(env, "DEV_MAIL_HOST"));
            locked.put("spring.mail.port", required(env, "DEV_MAIL_PORT"));
            locked.put("spring.mail.username", required(env, "DEV_MAIL_USERNAME"));
            locked.put("spring.mail.password", required(env, "DEV_MAIL_PASSWORD"));
            String recipients = required(env, "DEV_MAIL_ALLOWED_RECIPIENTS");
            for (String r : recipients.split(",", -1)) if (!r.trim().matches("[^\\s@,]+@[^\\s@,]+\\.[^\\s@,]+")) fail("Invalid development mail recipient allowlist");
            locked.put("app.mail.allowed-recipients", recipients);
        } else {
            locked.put("spring.mail.host", "127.0.0.1");
            locked.put("spring.mail.username", "disabled");
            locked.put("spring.mail.password", "disabled");
            locked.put("app.mail.allowed-recipients", "");
        }
        boolean oauth = enabled(env, "DEV_OAUTH_ENABLED");
        locked.put("app.oauth.enabled", oauth);
        for (String provider : List.of("google", "naver")) {
            String prefix = "spring.security.oauth2.client.registration." + provider + ".";
            String vars = "DEV_" + provider.toUpperCase(Locale.ROOT) + "_CLIENT_";
            locked.put(prefix + "client-id", oauth ? required(env, vars + "ID") : "disabled");
            locked.put(prefix + "client-secret", oauth ? required(env, vars + "SECRET") : "disabled");
            if (oauth) locked.put(prefix + "redirect-uri", origin(required(env, "DEV_BACKEND_ORIGIN"))
                + "/login/oauth2/code/" + provider);
        }
        if (!oauth) {
            String excludes = env.getProperty("spring.autoconfigure.exclude", "");
            locked.put("spring.autoconfigure.exclude", excludes.isBlank() ? OAUTH_AUTO_CONFIG : excludes + "," + OAUTH_AUTO_CONFIG);
        }
        boolean places = enabled(env, "DEV_PLACES_ENABLED");
        locked.put("app.places.enabled", places);
        locked.put("google.places.api-key", places ? required(env, "DEV_GOOGLE_PLACES_API_KEY") : "");
        env.getPropertySources().addFirst(new MapPropertySource("devDeploymentSafety", locked));
    }

    static void validateDatabaseUrl(String url, String user, String ref, String role, String db, String pooler) {
        if (!url.startsWith("jdbc:postgresql://")) fail("Development datasource must use a single PostgreSQL host");
        URI u = uri(url.substring(5), "Invalid development JDBC URL");
        if (u.getHost() == null || u.getUserInfo() != null || u.getFragment() != null || !u.getPath().equals("/" + db))
            fail("Development database address is outside the allowlist");
        boolean direct = u.getHost().equals("db." + ref + ".supabase.co");
        boolean sessionPooler = !pooler.isBlank() && pooler.matches("[a-z0-9-]+\\.pooler\\.supabase\\.com") && u.getHost().equals(pooler);
        if ((!direct && !sessionPooler) || (u.getPort() != -1 && u.getPort() != 5432)) fail("Only the approved direct host or session pooler on port 5432 is allowed");
        if (!user.equals(direct ? role : role + "." + ref)) fail("Development DB username does not match the approved role/project");
        Map<String,String> options = new HashMap<>();
        if (u.getRawQuery() != null) for (String pair : u.getRawQuery().split("&", -1)) {
            String[] parts = pair.split("=", -1);
            if (parts.length != 2 || options.put(parts[0], parts[1]) != null) fail("Invalid or duplicate JDBC option");
        }
        if (!"verify-full".equals(options.get("sslmode"))) fail("Development JDBC URL requires sslmode=verify-full");
        if (!"org.postgresql.ssl.DefaultJavaSSLFactory".equals(options.get("sslfactory"))) fail("Development JDBC URL requires the JVM certificate trust factory");
        for (var entry : options.entrySet()) {
            if (Set.of("sslmode", "sslfactory").contains(entry.getKey())) continue;
            if (!Set.of("connectTimeout", "socketTimeout").contains(entry.getKey()) || !entry.getValue().matches("[1-9][0-9]{0,2}"))
                fail("Unapproved JDBC connection option");
        }
    }

    private static void validateLegacyStorageKey(String key, String ref) {
        if (key.matches("sb_secret_[A-Za-z0-9_-]+")) return; // Opaque keys cannot be assigned to projects offline.
        try {
            String[] parts = key.split("\\.");
            var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (parts.length != 3 || !ref.equals(json.path("ref").asText()) || !"service_role".equals(json.path("role").asText()))
                fail("Storage JWT project/role does not match the development project");
        } catch (Exception e) { fail("Development storage requires a secret key or matching legacy service_role JWT"); }
    }
    static String required(Environment env, String name) {
        String v;
        try { v = env.getProperty(name); } catch (IllegalArgumentException e) { v = null; }
        if (v == null || v.isBlank() || v.contains("${")) fail("Missing required development setting: " + name);
        return v;
    }
    private static boolean enabled(Environment env, String name) {
        String v = env.getProperty(name, "false");
        if (!Set.of("true", "false").contains(v)) fail("Invalid integration flag: " + name);
        return Boolean.parseBoolean(v);
    }
    private static void rejectIfPresent(Environment env, String... names) {
        for (String name : names) if (!env.getProperty(name, "").isBlank()) fail("Unsupported development datasource override: " + name);
    }
    private static Set<String> origins(String list) {
        Set<String> result = new HashSet<>();
        for (String item : list.split(",", -1)) result.add(origin(item.trim()));
        return result;
    }
    private static String origin(String value) {
        URI u = uri(value, "Invalid development frontend origin");
        String host = u.getHost();
        if (!"https".equals(u.getScheme()) || host == null || u.getUserInfo() != null || u.getQuery() != null || u.getFragment() != null
            || !u.getPath().isEmpty() || host.equalsIgnoreCase("gwonsystem.com") || host.toLowerCase(Locale.ROOT).endsWith(".gwonsystem.com")
            || host.equalsIgnoreCase("gwonsystem-backend.onrender.com")
            || host.endsWith(".supabase.co")) fail("Development requires an explicit HTTPS origin outside production domains");
        return value;
    }
    private static URI uri(String value, String message) {
        try { return URI.create(value); } catch (IllegalArgumentException e) { fail(message); return null; }
    }
    static void fail(String reason) { throw new IllegalStateException("Development deployment blocked: " + reason); }
}
