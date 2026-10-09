package com.gwonsystem.backend.controller;

import com.gwonsystem.backend.dto.BannerResponse;
import com.gwonsystem.backend.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/banners")
@RequiredArgsConstructor
public class BannerController {
    private final BannerService gallery;

    @GetMapping
    public List<BannerResponse> list() { return gallery.list(); }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        var photo = gallery.image(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.getContentType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(photo.getImage());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) { gallery.delete(id); return ResponseEntity.noContent().build(); }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<BannerResponse> create(@RequestParam String title,
            @RequestParam(defaultValue = "") String description,
            @RequestPart("photo") MultipartFile photo, Authentication authentication) {
        var result = gallery.create(title, description, photo, authentication.getName());
        return ResponseEntity.created(URI.create("/api/banners/" + result.id() + "/image")).body(result);
    }
}
