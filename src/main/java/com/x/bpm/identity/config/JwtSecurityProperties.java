package com.x.bpm.identity.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cấu hình HS256; secret mặc định chỉ được cung cấp trong profile dev/test. */
@ConfigurationProperties(prefix = "bpm.security.jwt")
public record JwtSecurityProperties(String secretKey, String issuer, String audience,
                                     Duration accessTokenTtl) {
    public JwtSecurityProperties {
        if (secretKey == null || secretKey.isBlank()
                || secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET_KEY must contain at least 32 UTF-8 bytes");
        }
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT issuer and audience must not be blank");
        }
        if (accessTokenTtl == null || accessTokenTtl.getSeconds() < 1 || accessTokenTtl.getNano() != 0) {
            throw new IllegalArgumentException("JWT access token TTL must be a positive whole number of seconds");
        }
    }

    @Override
    public String toString() {
        return "JwtSecurityProperties[secretKey=<redacted>, issuer=" + issuer
                + ", audience=" + audience + ", accessTokenTtl=" + accessTokenTtl + "]";
    }
}
