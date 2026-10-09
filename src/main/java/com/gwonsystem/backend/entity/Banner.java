package com.gwonsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "home_banners")
@Getter
@NoArgsConstructor
public class Banner {
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
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private String createdBy;

    public Banner(String title, String description, String contentType, byte[] image, String createdBy) {
        this.title = title; this.description = description; this.contentType = contentType;
        this.image = image; this.createdBy = createdBy; this.createdAt = Instant.now();
    }
}
