package com.gwonsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "gallery_photos")
@Getter
@NoArgsConstructor
public class GalleryPhoto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 80)
    private String title;
    @Column(nullable = false, length = 300)
    private String description;
    @Column(nullable = false, length = 30)
    private String contentType;
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] image;
    @Column(length = 160)
    private String storagePath;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private String createdBy;

    public static GalleryPhoto stored(String title, String description, String contentType, String path, String username) {
        GalleryPhoto photo = new GalleryPhoto(title, description, contentType, new byte[0], username);
        photo.storagePath = path;
        return photo;
    }

    public GalleryPhoto(String title, String description, String contentType, byte[] image, String createdBy) {
        this.title = title; this.description = description; this.contentType = contentType;
        this.image = image; this.createdBy = createdBy; this.createdAt = Instant.now();
    }
}
