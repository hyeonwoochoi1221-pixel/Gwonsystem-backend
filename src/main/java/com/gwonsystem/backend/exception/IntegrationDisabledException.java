package com.gwonsystem.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A deliberately unavailable integration, rather than an unexpected application failure. */
public final class IntegrationDisabledException extends ResponseStatusException {
    public IntegrationDisabledException(String reason) { super(HttpStatus.SERVICE_UNAVAILABLE, reason); }
}
