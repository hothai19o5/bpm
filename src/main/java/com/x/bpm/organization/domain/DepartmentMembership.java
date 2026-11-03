package com.x.bpm.organization.domain;

import java.util.Objects;
import java.util.UUID;

public record DepartmentMembership(UUID id, UUID userId, UUID departmentId, UUID positionId,
        long revision) {
    public DepartmentMembership {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(departmentId, "departmentId");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
    }
}
