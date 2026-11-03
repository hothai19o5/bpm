package com.x.bpm.identity.application;

import java.util.Objects;

/** Refresh token gốc chỉ tồn tại trong application flow; persistence chỉ nhận hash. */
public record IssuedRefreshToken(String rawToken, String tokenHash) {
    public IssuedRefreshToken {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("rawToken must not be blank");
        }
        Objects.requireNonNull(tokenHash, "tokenHash");
        if (!tokenHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("tokenHash must be a lowercase SHA-256 hex value");
        }
    }

    @Override
    public String toString() {
        return "IssuedRefreshToken[rawToken=<redacted>, tokenHash=<redacted>]";
    }
}
