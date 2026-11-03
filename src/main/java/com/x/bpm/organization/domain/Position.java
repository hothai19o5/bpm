package com.x.bpm.organization.domain;

import java.util.Objects;
import java.util.UUID;

public record Position(UUID id, String code, String name, String description,
        OrganizationStatus status, long revision) {
    public Position {
        Objects.requireNonNull(id, "id");
        code = requireText(code, "code");
        name = requireText(name, "name");
        description = description == null ? null : description.strip();
        Objects.requireNonNull(status, "status");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
