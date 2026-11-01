package com.x.bpm.identity.application;

import java.time.Instant;

/** Dữ liệu trả ở login/refresh; tránh log token qua toString. */
public record IssuedAccessToken(String accessToken, String tokenType, Instant expiresAt, long expiresIn) {
    @Override
    public String toString() {
        return "IssuedAccessToken[accessToken=<redacted>, tokenType=" + tokenType
                + ", expiresAt=" + expiresAt + ", expiresIn=" + expiresIn + "]";
    }
}
