package com.x.bpm.organization.domain;

import java.util.Objects;
import java.util.UUID;

/** Phòng ban độc lập với JPA; quan hệ cây được kiểm tra trong use case. */
public record Department(UUID id, String code, String name, UUID parentId, UUID managerUserId,
        OrganizationStatus status, long revision) {
    public Department {
        Objects.requireNonNull(id, "id");
        code = requireText(code, "code");
        name = requireText(name, "name");
        Objects.requireNonNull(status, "status");
        if (id.equals(parentId)) {
            throw new IllegalArgumentException("Department cannot be its own parent");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
