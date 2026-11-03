package com.x.bpm.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Metadata bền vững của refresh token; chỉ chứa SHA-256 hash, không chứa raw token. */
public final class RefreshCredential {
    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final UUID id;
    private final UUID grantId;
    private final String tokenHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Instant consumedAt;
    private final long revision;

    public RefreshCredential(UUID id, UUID grantId, String tokenHash, Instant createdAt,
            Instant expiresAt, Instant consumedAt, long revision) {
        this.id = Objects.requireNonNull(id, "id");
        this.grantId = Objects.requireNonNull(grantId, "grantId");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!SHA_256_HEX.matcher(tokenHash).matches()) {
            throw new IllegalArgumentException("tokenHash must be a lowercase SHA-256 hex value");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        if (consumedAt != null && consumedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("consumedAt must not be before createdAt");
        }
        if (consumedAt != null && !consumedAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("consumedAt must be before expiresAt");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        this.consumedAt = consumedAt;
        this.revision = revision;
    }

    public UUID id() {
        return id;
    }

    public UUID grantId() {
        return grantId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant consumedAt() {
        return consumedAt;
    }

    public long revision() {
        return revision;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isUsableAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return consumedAt == null && instant.isBefore(expiresAt);
    }

    public RefreshCredential consume(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        if (consumedAt != null) {
            throw new IllegalStateException("Refresh credential has already been consumed");
        }
        if (instant.isBefore(createdAt)) {
            throw new IllegalArgumentException("consumedAt must not be before createdAt");
        }
        if (!instant.isBefore(expiresAt)) {
            throw new IllegalArgumentException("Cannot consume an expired refresh credential");
        }
        return new RefreshCredential(id, grantId, tokenHash, createdAt, expiresAt, instant,
                revision);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof RefreshCredential credential
                && id.equals(credential.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "RefreshCredential[id=" + id + ", grantId=" + grantId
                + ", tokenHash=<redacted>, expiresAt=" + expiresAt + ", consumedAt="
                + consumedAt + ", revision=" + revision + "]";
    }
}
