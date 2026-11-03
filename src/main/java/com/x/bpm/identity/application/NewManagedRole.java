package com.x.bpm.identity.application;

import java.util.Set;
import java.util.UUID;

public record NewManagedRole(String code, String name, String description, boolean active,
        Set<UUID> permissionIds) {
    public NewManagedRole {
        permissionIds = permissionIds == null ? Set.of() : Set.copyOf(permissionIds);
    }
}
