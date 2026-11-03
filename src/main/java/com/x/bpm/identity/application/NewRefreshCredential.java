package com.x.bpm.identity.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Dữ liệu hash cần lưu cho refresh token mới. */
public record NewRefreshCredential(UUID grantId, String tokenHash, Instant expiresAt) {
    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

    public NewRefreshCredential {
        Objects.requireNonNull(grantId, "grantId");
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!SHA_256_HEX.matcher(tokenHash).matches()) {
            throw new IllegalArgumentException("tokenHash must be a lowercase SHA-256 hex value");
        }
    }

    @Override
    public String toString() {
        return "NewRefreshCredential[grantId=" + grantId + ", tokenHash=<redacted>, expiresAt="
                + expiresAt + "]";
    }
}
