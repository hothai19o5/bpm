package com.x.bpm.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Một lần đăng nhập có thời hạn và có thể bị thu hồi phía server. */
public final class LoginGrant {
    private final UUID id;
    private final UUID userId;
    private final long securityVersion;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Instant revokedAt;
    private final String revokeReason;
    private final long revision;

    public LoginGrant(UUID id, UUID userId, long securityVersion, Instant createdAt,
            Instant expiresAt, Instant revokedAt, String revokeReason, long revision) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (securityVersion < 0 || revision < 0) {
            throw new IllegalArgumentException("securityVersion and revision must not be negative");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        if (revokedAt != null && revokedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("revokedAt must not be before createdAt");
        }
        if (revokeReason != null && revokeReason.isBlank()) {
            throw new IllegalArgumentException("revokeReason must not be blank when present");
        }
        this.securityVersion = securityVersion;
        this.revokedAt = revokedAt;
        this.revokeReason = revokeReason;
        this.revision = revision;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public long securityVersion() {
        return securityVersion;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public String revokeReason() {
        return revokeReason;
    }

    public long revision() {
        return revision;
    }

    public boolean isUsableAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return revokedAt == null && instant.isBefore(expiresAt);
    }

    public LoginGrant revoke(Instant instant, String reason) {
        Objects.requireNonNull(instant, "instant");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("revoke reason must not be blank");
        }
        if (instant.isBefore(createdAt)) {
            throw new IllegalArgumentException("revokedAt must not be before createdAt");
        }
        if (revokedAt != null) {
            return this;
        }
        return new LoginGrant(id, userId, securityVersion, createdAt, expiresAt, instant,
                reason.strip(), revision);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof LoginGrant grant && id.equals(grant.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
