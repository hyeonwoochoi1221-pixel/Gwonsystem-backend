package com.gwonsystem.backend.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** All DDL fixtures are confined to a NEW disposable localhost cluster, never Supabase. */
class DevTemporaryPrivilegeTest {
    // Embedded PostgreSQL has no TLS. Mock only that field; all privilege results are real.
    Connection withTlsProbe(Connection actual) throws Exception {
        var connection = spy(actual);
        doAnswer(call -> {
            var original = actual.createStatement();
            var statement = spy(original);
            doAnswer(query -> {
                var rows = spy(original.executeQuery(query.getArgument(0)));
                doReturn(true).when(rows).getBoolean(3);
                return rows;
            }).when(statement).executeQuery(anyString());
            return statement;
        }).when(connection).createStatement();
        return connection;
    }

    @Test void tempObjectsAndCatalogShadowingDoNotBypassPermanentDdlChecks() throws Exception {
        var env = DevEnvironmentGuardTest.environment(DevEnvironmentGuardTest.settings());
        DevEnvironmentGuardTest.apply(env);
        assertEquals("public,pg_temp", env.getProperty("spring.datasource.hikari.data-source-properties.currentSchema"));
        assertEquals("public", env.getProperty("spring.jpa.properties.hibernate.default_schema"));
        try (var pg = EmbeddedPostgres.builder().setPort(0).start();
             var admin = pg.getPostgresDatabase().getConnection()) {
            String url = admin.getMetaData().getURL();
            assertTrue(url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/postgres.*"));
            try (var s = admin.createStatement()) {
                s.execute("CREATE ROLE gwon_dev_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
                s.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
                s.execute("CREATE TABLE public.guard_fixture (id integer)");
                s.execute("GRANT USAGE ON SCHEMA public TO gwon_dev_app");
                s.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON public.guard_fixture TO gwon_dev_app");
            }
            try (var app = DriverManager.getConnection(url, "gwon_dev_app", "")) {
                try (var s = app.createStatement(); var rows = s.executeQuery(
                        "SELECT has_database_privilege(current_user,current_database(),'TEMP')")) {
                    assertTrue(rows.next());
                    assertTrue(rows.getBoolean(1));
                }
                var probe = withTlsProbe(app);
                DevDataSourceGuard.verifyPeer(probe, "postgres", "gwon_dev_app");
                try (var s = app.createStatement()) {
                    s.execute("CREATE TEMP TABLE guard_fixture (id integer)");
                    s.execute("ALTER TABLE pg_temp.guard_fixture ADD COLUMN temporary_only integer");
                    s.execute("CREATE TEMP TABLE pg_roles (rolname text)");
                    s.execute("SET search_path = pg_temp, public");
                    assertThrows(SQLException.class, () -> s.execute("CREATE TABLE public.forbidden (id integer)"));
                    assertThrows(SQLException.class, () -> s.execute("ALTER TABLE public.guard_fixture ADD COLUMN forbidden integer"));
                    assertThrows(SQLException.class, () -> s.execute("DROP TABLE public.guard_fixture"));
                }
                DevDataSourceGuard.verifyPeer(probe, "postgres", "gwon_dev_app");
                try (var s = admin.createStatement()) {
                    s.execute("CREATE SCHEMA extra_schema");
                    s.execute("GRANT CREATE ON SCHEMA extra_schema TO gwon_dev_app");
                }
                assertThrows(SQLException.class, () -> DevDataSourceGuard.verifyPeer(probe, "postgres", "gwon_dev_app"));
                try (var s = admin.createStatement()) {
                    s.execute("REVOKE CREATE ON SCHEMA extra_schema FROM gwon_dev_app");
                    s.execute("ALTER TABLE public.guard_fixture OWNER TO gwon_dev_app");
                }
                assertThrows(SQLException.class, () -> DevDataSourceGuard.verifyPeer(probe, "postgres", "gwon_dev_app"));
            }
        }
    }
}
