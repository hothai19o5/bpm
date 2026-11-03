package com.x.bpm.identity.application;

import java.util.Objects;
import java.util.UUID;

public record ManagedRole(UUID id, String code, String name, boolean active) {
    public ManagedRole {
        Objects.requireNonNull(id, "id");
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("role code and name must not be blank");
        }
    }
}
