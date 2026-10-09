package com.gwonsystem.backend.dto;

import java.time.Instant;

public record BannerResponse(Long id, String title, String description, Instant createdAt) {}
