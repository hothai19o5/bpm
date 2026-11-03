package com.x.bpm.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cấu hình vòng đời credential không thuộc chi tiết JWT. */
@ConfigurationProperties(prefix = "bpm.security.authentication")
public record AuthenticationProperties(Duration refreshTokenTtl) {
    public AuthenticationProperties {
        if (refreshTokenTtl == null || refreshTokenTtl.isZero() || refreshTokenTtl.isNegative()) {
            throw new IllegalArgumentException("Refresh token TTL must be positive");
        }
    }
}
