package com.gwonsystem.backend.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real pgJDBC/JSSE handshakes; generated test keys live only in disposable temp files. */
class PgJdbcTlsTest {
    @TempDir Path directory;

    void run(List<String> command) throws Exception {
        var process=new ProcessBuilder(command).redirectErrorStream(true)
            .redirectOutput(directory.resolve("probe.log").toFile()).start();
        if (!process.waitFor(30,TimeUnit.SECONDS)) {
            process.destroyForcibly();fail("Local TLS test process timed out");
        }
        assertEquals(0,process.exitValue(),()-> {
            try { return Files.readString(directory.resolve("probe.log")); }
            catch(Exception ignored) { return "Local TLS probe failed"; }
        });
    }

    String binary(String name) {
        return Path.of(System.getProperty("java.home"),"bin",name+
            (System.getProperty("os.name").startsWith("Windows")?".exe":"")).toString();
    }

    void probe(String url,Path truststore,String expectation) throws Exception {
        run(List.of(binary("java"),"-Djavax.net.ssl.trustStore="+truststore,
            "-Djavax.net.ssl.trustStorePassword=local-test-only","-cp",
            System.getProperty("java.class.path"),PgJdbcTlsProbe.class.getName(),url,expectation));
    }

    @Test void verifyFullRequiresTrustedCaMatchingHostnameAndTlsSupport() throws Exception {
        String password="local-test-only"; // disposable fixture, never a deployment credential
        Path keys=directory.resolve("keys.p12"),certificate=directory.resolve("server.crt"),key=directory.resolve("server.key");
        Path trusted=directory.resolve("trusted.p12"),empty=directory.resolve("empty.p12");
        run(List.of(binary("keytool"),"-genkeypair","-alias","local-server","-keyalg","RSA","-keysize","2048",
            "-dname","CN=localhost","-ext","SAN=dns:localhost","-ext","BC=ca:true","-validity","2",
            "-keystore",keys.toString(),"-storetype","PKCS12","-storepass",password,"-keypass",password,"-noprompt"));
        var keystore=KeyStore.getInstance("PKCS12");
        try(var input=Files.newInputStream(keys)) { keystore.load(input,password.toCharArray()); }
        var encoder=Base64.getMimeEncoder(64,new byte[]{'\n'});
        Files.writeString(certificate,"-----BEGIN CERTIFICATE-----\n"+
            encoder.encodeToString(keystore.getCertificate("local-server").getEncoded())+"\n-----END CERTIFICATE-----\n");
        Files.writeString(key,"-----BEGIN PRIVATE KEY-----\n"+
            encoder.encodeToString(keystore.getKey("local-server",password.toCharArray()).getEncoded())+"\n-----END PRIVATE KEY-----\n");
        if (Files.getFileStore(key).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(key,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        var trust=KeyStore.getInstance("PKCS12");trust.load(null,password.toCharArray());
        try(var output=Files.newOutputStream(empty)) { trust.store(output,password.toCharArray()); }
        trust.setCertificateEntry("local-ca",keystore.getCertificate("local-server"));
        try(var output=Files.newOutputStream(trusted)) { trust.store(output,password.toCharArray()); }
        String options="?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&connectTimeout=5&socketTimeout=5";
        try(var pg=EmbeddedPostgres.builder().setPort(0).setServerConfig("ssl","on")
                .setServerConfig("ssl_cert_file",certificate.toString().replace('\\','/'))
                .setServerConfig("ssl_key_file",key.toString().replace('\\','/')).start()) {
            String url;
            try(var connection=pg.getPostgresDatabase().getConnection()) { url=connection.getMetaData().getURL().split("\\?")[0]; }
            assertTrue(url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/postgres"));
            String localhost=url.replace("127.0.0.1","localhost");
            probe(localhost+options,trusted,"success");
            probe(localhost+options,empty,"reject");
            probe(localhost.replace("localhost","127.0.0.1")+options,trusted,"reject");
        }
        try(var pg=EmbeddedPostgres.builder().setPort(0).setServerConfig("ssl","off").start()) {
            String url;
            try(var connection=pg.getPostgresDatabase().getConnection()) { url=connection.getMetaData().getURL().split("\\?")[0]; }
            probe(url+options,trusted,"reject");
        }
    }
}
