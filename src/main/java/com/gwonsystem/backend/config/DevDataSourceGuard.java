package com.gwonsystem.backend.config;

import java.sql.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/** Verify the authenticated peer before Hibernate or application code receives a connection. */
@Configuration(proxyBeanMethods = false)
@Profile("dev")
public class DevDataSourceGuard {
    @Bean static BeanPostProcessor developmentDataSourceVerifier(Environment env) {
        String database = DevEnvironmentGuard.required(env, "DEV_DB_NAME");
        String role = DevEnvironmentGuard.required(env, "DEV_DB_ROLE");
        String ref = DevEnvironmentGuard.required(env, "DEV_SUPABASE_PROJECT_REF");
        String user = DevEnvironmentGuard.required(env, "spring.datasource.username");
        String pooler = env.getProperty("DEV_DB_POOLER_HOST", "");
        return new BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean, String name) {
                return bean instanceof DataSource ds ? new VerifiedDataSource(ds, database, role, ref, user, pooler) : bean;
            }
        };
    }

    static final class VerifiedDataSource extends DelegatingDataSource implements AutoCloseable {
        private final String database;
        private final String role;
        private final String ref;
        private final String user;
        private final String pooler;
        VerifiedDataSource(DataSource target, String database, String role, String ref, String user, String pooler) {
            super(target); this.database = database; this.role = role; this.ref = ref; this.user = user; this.pooler = pooler;
        }
        @Override public Connection getConnection() throws SQLException { return verify(super.getConnection()); }
        @Override public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("Development deployment blocked: alternate connection credentials are forbidden");
        }
        @Override public void close() throws Exception {
            if (getTargetDataSource() instanceof AutoCloseable closeable) closeable.close();
        }
        private Connection verify(Connection c) throws SQLException {
            try {
                String url = c.getMetaData().getURL();
                DevEnvironmentGuard.validateDatabaseUrl(url, user, ref, role, database, pooler);
                // Only an approved Session pooler terminates the client's TLS separately.
                // pgJDBC verify-full + DefaultJavaSSLFactory must succeed before a
                // connection exists; the metadata allowlist rechecks that configuration.
                boolean sessionPooler = !pooler.isBlank()
                    && pooler.equals(java.net.URI.create(url.substring(5)).getHost());
                verifyPeer(c, database, role, !sessionPooler);
                return c;
            } catch (Exception e) {
                try { c.close(); } catch (SQLException ignored) { }
                // Do not attach underlying exceptions: driver messages can expose connection details.
                throw new SQLException("Development deployment blocked: database identity, TLS or role privileges failed verification");
            }
        }
    }

    static void verifyPeer(Connection c, String database, String role) throws SQLException {
        verifyPeer(c, database, role, true);
    }

    static void verifyPeer(Connection c, String database, String role, boolean requireBackendTls) throws SQLException {
        // This probe never changes schema/data. Restore connection state before handing it to JPA.
        boolean auto = c.getAutoCommit();
        boolean readOnly = c.isReadOnly();
        try {
            c.setReadOnly(true);
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                s.setQueryTimeout(5);
                try (ResultSet r = s.executeQuery("SELECT pg_catalog.current_database(), current_user, "
                    + "COALESCE((SELECT ssl FROM pg_catalog.pg_stat_ssl WHERE pid=pg_catalog.pg_backend_pid()), false), "
                    + "EXISTS(SELECT 1 FROM pg_catalog.pg_roles WHERE rolname=current_user AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)), "
                    + "EXISTS(SELECT 1 FROM pg_catalog.pg_namespace n WHERE n.nspname !~ '^pg_(toast_)?temp_' "
                    + "AND pg_catalog.has_schema_privilege(current_user,n.oid,'CREATE')), "
                    + "EXISTS(SELECT 1 FROM pg_catalog.pg_shdepend d WHERE d.refclassid='pg_catalog.pg_authid'::regclass AND d.deptype='o' "
                    + "AND pg_catalog.pg_has_role(current_user,d.refobjid,'MEMBER') "
                    + "AND (d.dbid=0 OR d.dbid=(SELECT oid FROM pg_catalog.pg_database WHERE datname=pg_catalog.current_database())) "
                    + "AND NOT (d.classid='pg_catalog.pg_class'::regclass AND EXISTS(SELECT 1 FROM pg_catalog.pg_class c WHERE c.oid=d.objid AND c.relpersistence='t')) "
                    + "AND NOT (d.classid='pg_catalog.pg_type'::regclass AND EXISTS(SELECT 1 FROM pg_catalog.pg_type t JOIN pg_catalog.pg_namespace n ON n.oid=t.typnamespace "
                    + "WHERE t.oid=d.objid AND n.nspname ~ '^pg_(toast_)?temp_')) "
                    + "AND NOT (d.classid='pg_catalog.pg_namespace'::regclass AND EXISTS(SELECT 1 FROM pg_catalog.pg_namespace n "
                    + "WHERE n.oid=d.objid AND n.nspname ~ '^pg_(toast_)?temp_'))), "
                    + "pg_catalog.has_database_privilege(current_user,pg_catalog.current_database(),'CREATE'), "
                    + "EXISTS(SELECT 1 FROM pg_catalog.pg_roles r WHERE r.rolname<>current_user AND pg_catalog.pg_has_role(current_user,r.oid,'MEMBER')), "
                    + "pg_catalog.has_database_privilege(current_user,pg_catalog.current_database(),'TEMP')")) {
                    // Column 3 is PostgreSQL-side TLS, NOT client-to-pooler TLS.
                    // Column 9 (TEMP) is informational: it grants no permanent schema DDL.
                    if (!r.next() || !database.equals(r.getString(1)) || !role.equals(r.getString(2))
                        || (requireBackendTls && !r.getBoolean(3)) || r.getBoolean(4) || r.getBoolean(5) || r.getBoolean(6) || r.getBoolean(7) || r.getBoolean(8))
                        throw new SQLException("Development database verification refused");
                }
            }
        } finally {
            c.rollback();
            c.setAutoCommit(auto);
            c.setReadOnly(readOnly);
        }
    }
}
