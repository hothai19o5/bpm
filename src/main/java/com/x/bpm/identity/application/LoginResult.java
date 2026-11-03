package com.x.bpm.identity.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Kết quả login để adapter web định dạng response. */
public record LoginResult(IssuedAccessToken accessToken, String refreshToken,
        Instant refreshExpiresAt, boolean mustChangePassword, UserSummary user) {
    public LoginResult {
        Objects.requireNonNull(accessToken, "accessToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken must not be blank");
        }
        Objects.requireNonNull(refreshExpiresAt, "refreshExpiresAt");
        Objects.requireNonNull(user, "user");
    }

    @Override
    public String toString() {
        return "LoginResult[accessToken=<redacted>, refreshToken=<redacted>, refreshExpiresAt="
                + refreshExpiresAt + ", mustChangePassword=" + mustChangePassword
                + ", user=" + user + "]";
    }

    public record UserSummary(UUID id, String username, String displayName,
            Set<String> permissions) {
        public UserSummary {
            Objects.requireNonNull(id, "id");
            if (username == null || username.isBlank()
                    || displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("username and displayName must not be blank");
            }
            permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
        }
    }
}
