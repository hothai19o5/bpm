package com.x.bpm.identity.application;

import com.x.bpm.identity.domain.ActorContext;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Snapshot danh tính đã được kiểm tra từ dữ liệu server cho một request. */
public record AuthenticatedAccount(UUID userId, String username, String displayName,
        Set<String> permissions, boolean mustChangePassword, UUID loginGrantId,
        long securityVersion) {
    public AuthenticatedAccount {
        Objects.requireNonNull(userId, "userId");
        if (username == null || username.isBlank() || displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("username and displayName must not be blank");
        }
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
        Objects.requireNonNull(loginGrantId, "loginGrantId");
        if (securityVersion < 0) {
            throw new IllegalArgumentException("securityVersion must not be negative");
        }
    }

    public ActorContext actorContext() {
        return new ActorContext(userId.toString(), true, permissions);
    }
}
