package com.x.bpm.identity.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Dữ liệu cần để mở một lần đăng nhập mới. */
public record NewLoginGrant(UUID userId, long securityVersion, Instant expiresAt) {
    public NewLoginGrant {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (securityVersion < 0) {
            throw new IllegalArgumentException("securityVersion must not be negative");
        }
    }
}
