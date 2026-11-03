package com.x.bpm.identity.application;

import java.util.Set;
import java.util.UUID;

public record ManagedRoleDetails(UUID id, String code, String name, String description,
        boolean active, long revision, Set<ManagedPermission> permissions) {
    public ManagedRoleDetails {
        permissions = Set.copyOf(permissions);
    }
}
