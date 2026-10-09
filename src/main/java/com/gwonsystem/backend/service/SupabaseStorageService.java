package com.gwonsystem.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@Service
public class SupabaseStorageService {
    private final RestClient client;
    private final String url;
    private final String bucket;
    private final String key;

    public SupabaseStorageService(RestClient.Builder builder,
            @Value("${supabase.storage.url}") String url,
            @Value("${supabase.storage.bucket}") String bucket,
            @Value("${supabase.storage.secret-key:}") String key) {
        this.url = url.replaceAll("/+$", "");
        this.bucket = bucket;
        this.key = key.trim();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(20000);
        this.client = builder.baseUrl(this.url + "/storage/v1").requestFactory(factory)
            .defaultHeaders(headers -> {
                headers.set("apikey", this.key);
                // New secret keys use apikey; legacy service_role keys are JWTs.
                if (this.key.startsWith("eyJ")) headers.setBearerAuth(this.key);
            }).build();
    }

    public String upload(byte[] bytes, String contentType) {
        if (key.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "사진 저장소가 설정되지 않았습니다. 관리자에게 문의해 주세요.");
        String path = "gallery/" + UUID.randomUUID() + (contentType.equals("image/png") ? ".png" : ".jpg");
        try {
            client.post().uri("/object/{bucket}/{folder}/{file}", bucket, "gallery", path.substring(8))
                .contentType(MediaType.parseMediaType(contentType)).header("x-upsert", "false")
                .body(bytes).retrieve().toBodilessEntity();
            return path;
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "사진 저장소 업로드에 실패했습니다.");
        }
    }

    public String publicUrl(String path) {
        if (path == null) return null;
        if (!path.matches("gallery/[a-zA-Z0-9-]+\\.(png|jpg)")) throw new IllegalArgumentException("Invalid gallery storage path");
        return url + "/storage/v1/object/public/" + bucket + "/" + path;
    }

    public void delete(String path) {
        publicUrl(path);
        client.method(HttpMethod.DELETE).uri("/object/{bucket}", bucket)
            .contentType(MediaType.APPLICATION_JSON).body(Map.of("prefixes", List.of(path)))
            .retrieve().toBodilessEntity();
    }
}
