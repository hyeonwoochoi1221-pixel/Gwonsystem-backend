package com.gwonsystem.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_id")
    private Long authorId;

    @Column(length = 30)
    private String category; // 보안, 인사, 인프라, 행사 등

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "view_count")
    private Integer viewCount;

    @Column(name = "is_pinned")
    private Boolean isPinned;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.viewCount = this.viewCount == null ? 0 : this.viewCount;
        this.isPinned = this.isPinned == null ? false : this.isPinned;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    public Notice(Long authorId, String category, String title, String content, Boolean isPinned) {
        this.authorId = authorId;
        this.category = category;
        this.title = title;
        this.content = content;
        this.isPinned = isPinned;
    }

    public void increaseViewCount() {
        this.viewCount++;
    }
}