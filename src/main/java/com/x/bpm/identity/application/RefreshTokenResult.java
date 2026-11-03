package com.x.bpm.identity.application;

import java.time.Instant;
import java.util.Objects;

/** Cặp token mới sau rotation; mọi secret đều được che khỏi toString. */
public record RefreshTokenResult(IssuedAccessToken accessToken, String refreshToken,
        Instant refreshExpiresAt, boolean mustChangePassword, LoginResult.UserSummary user) {
    public RefreshTokenResult {
        Objects.requireNonNull(accessToken, "accessToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken must not be blank");
        }
        Objects.requireNonNull(refreshExpiresAt, "refreshExpiresAt");
        Objects.requireNonNull(user, "user");
    }

    @Override
    public String toString() {
        return "RefreshTokenResult[accessToken=<redacted>, refreshToken=<redacted>, "
                + "refreshExpiresAt=" + refreshExpiresAt + ", mustChangePassword="
                + mustChangePassword + ", user=" + user + "]";
    }
}
