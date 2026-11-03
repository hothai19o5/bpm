package com.x.bpm.task.assignment.application;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AssignmentUser(UUID id, boolean active, UUID departmentId, Set<UUID> roleIds,
        Set<String> permissions) {
    public AssignmentUser {
        Objects.requireNonNull(id, "id");
        roleIds = Set.copyOf(Objects.requireNonNullElse(roleIds, Set.of()));
        permissions = Set.copyOf(Objects.requireNonNullElse(permissions, Set.of()));
    }

    public boolean hasPermission(String permission) {
        return permission == null || permissions.contains(permission);
    }
}
