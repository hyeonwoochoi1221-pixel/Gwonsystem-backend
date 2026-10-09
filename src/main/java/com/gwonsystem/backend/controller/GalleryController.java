package com.gwonsystem.backend.controller;

import com.gwonsystem.backend.dto.GalleryPhotoResponse;
import com.gwonsystem.backend.service.GalleryService;
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
@RequestMapping("/api/gallery")
@RequiredArgsConstructor
public class GalleryController {
    private final GalleryService gallery;

    @GetMapping
    public List<GalleryPhotoResponse> list() { return gallery.list(); }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        var photo = gallery.image(id);
        String publicUrl = gallery.publicImageUrl(photo);
        if (publicUrl != null) return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(publicUrl)).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.getContentType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(photo.getImage());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('REGULAR', 'SUPERVISOR')")
    public ResponseEntity<GalleryPhotoResponse> create(@RequestParam String title,
            @RequestParam(defaultValue = "") String description,
            @RequestPart("photo") MultipartFile photo, Authentication authentication) {
        var result = gallery.create(title, description, photo, authentication.getName());
        return ResponseEntity.created(URI.create("/api/gallery/" + result.id() + "/image")).body(result);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('REGULAR', 'SUPERVISOR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        gallery.delete(id);
        return ResponseEntity.noContent().build();
    }
}
