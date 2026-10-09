package com.gwonsystem.backend.dto;
import java.time.Instant;
public record GalleryPhotoResponse(Long id, String title, String description, Instant createdAt, String imageUrl) {
    public GalleryPhotoResponse(Long id, String title, String description, Instant createdAt) {
        this(id, title, description, createdAt, null);
    }
}
