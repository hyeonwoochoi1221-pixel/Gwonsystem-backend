package com.gwonsystem.backend.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;

@RestController
@Profile("dev")
public class HealthController {
    @GetMapping("/internal/health") public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
