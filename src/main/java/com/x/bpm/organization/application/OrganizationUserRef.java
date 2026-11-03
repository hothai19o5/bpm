package com.x.bpm.organization.application;

import java.util.Objects;
import java.util.UUID;

public record OrganizationUserRef(UUID id, String username, String displayName, boolean active) {
    public OrganizationUserRef {
        Objects.requireNonNull(id, "id");
        if (username == null || username.isBlank() || displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("username and displayName must not be blank");
        }
    }
}
