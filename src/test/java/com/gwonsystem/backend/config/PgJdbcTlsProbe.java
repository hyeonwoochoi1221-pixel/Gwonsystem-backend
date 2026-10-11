package com.gwonsystem.backend.config;

import java.sql.DriverManager;

/** Forked localhost-only probe: isolates JSSE's cached default socket factory. */
public final class PgJdbcTlsProbe {
    public static void main(String[] args) throws Exception {
        if (!args[0].matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/postgres\\?.*"))
            throw new IllegalArgumentException("Disposable localhost endpoint required");
        try (var connection=DriverManager.getConnection(args[0],"postgres","")) {
            if (!"success".equals(args[1])) throw new AssertionError("Unverified connection returned");
            try (var statement=connection.createStatement(); var rows=statement.executeQuery(
                    "SELECT ssl FROM pg_catalog.pg_stat_ssl WHERE pid=pg_catalog.pg_backend_pid()")) {
                if (!rows.next() || !rows.getBoolean(1)) throw new AssertionError("Local test TLS missing");
            }
        } catch (java.sql.SQLException failure) {
            if (!"reject".equals(args[1])) throw failure;
        }
    }
}
