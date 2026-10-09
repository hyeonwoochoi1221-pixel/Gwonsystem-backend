package com.gwonsystem.backend;
import com.gwonsystem.backend.service.SupabaseStorageService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SupabaseStorageServiceTest {
    HttpServer server;
    String url;
    AtomicReference<String> path = new AtomicReference<>();
    AtomicReference<String> key = new AtomicReference<>();
    AtomicReference<String> auth = new AtomicReference<>();
    AtomicReference<String> body = new AtomicReference<>();
    int status = 200;
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/storage/v1", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            key.set(exchange.getRequestHeaders().getFirst("apikey"));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status, 2);
            exchange.getResponseBody().write("{}".getBytes()); exchange.close();
        });
        server.start(); url = "http://127.0.0.1:" + server.getAddress().getPort();
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void uploadsWithServerKeyAndDeletesOnlyGalleryObject() {
        var storage = new SupabaseStorageService(RestClient.builder(), url, "public-images", "sb_secret_test");
        String object = storage.upload(new byte[]{1,2}, "image/png");
        assertTrue(object.matches("gallery/[a-f0-9-]+\\.png"));
        assertEquals("/storage/v1/object/public-images/" + object, path.get());
        assertEquals("sb_secret_test", key.get()); assertNull(auth.get());
        assertEquals(url + "/storage/v1/object/public/public-images/" + object, storage.publicUrl(object));
        storage.delete(object);
        assertEquals("/storage/v1/object/public-images", path.get());
        assertTrue(body.get().contains(object));
        assertThrows(IllegalArgumentException.class, () -> storage.delete("Main_Logo.png"));
    }
    @Test void missingConfigurationDoesNotSendRequest() {
        var storage = new SupabaseStorageService(RestClient.builder(), url, "public-images", "");
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> storage.upload(new byte[]{1}, "image/png")).getStatusCode().value());
        assertNull(path.get());
    }
    @Test void upstreamFailureIsSanitized() {
        status = 403;
        var storage = new SupabaseStorageService(RestClient.builder(), url, "public-images", "sb_secret_test");
        var failure = assertThrows(ResponseStatusException.class, () -> storage.upload(new byte[]{1}, "image/png"));
        assertEquals(502, failure.getStatusCode().value());
        assertFalse(failure.getMessage().contains("sb_secret_test"));
    }
}
