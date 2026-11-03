package com.x.bpm.identity.application;

import java.util.Objects;
import java.util.UUID;

public record ManagedPermission(UUID id, String code, String name, String description) {
    public ManagedPermission {
        Objects.requireNonNull(id, "id");
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("permission code and name must not be blank");
        }
    }
}
