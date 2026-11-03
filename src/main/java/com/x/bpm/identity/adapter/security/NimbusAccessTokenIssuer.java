package com.x.bpm.identity.adapter.security;

import com.x.bpm.identity.application.AccessTokenIssuer;
import com.x.bpm.identity.application.IssuedAccessToken;
import com.x.bpm.identity.config.JwtSecurityProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Adapter phát token; không cung cấp endpoint cấp token khi chưa xác thực. */
@Component
public class NimbusAccessTokenIssuer implements AccessTokenIssuer {
    private final JwtEncoder encoder;
    private final JwtSecurityProperties properties;
    private final Clock clock;

    public NimbusAccessTokenIssuer(JwtEncoder encoder, JwtSecurityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public IssuedAccessToken issue(UUID userId, UUID loginGrantId, long securityVersion,
            Set<String> permissions) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(loginGrantId, "loginGrantId");
        if (securityVersion < 0) {
            throw new IllegalArgumentException("securityVersion must not be negative");
        }
        Objects.requireNonNull(permissions, "permissions");
        if (permissions.stream().anyMatch(permission -> permission == null || permission.isBlank())) {
            throw new IllegalArgumentException("permissions must contain non-blank values");
        }
        var now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        var expiresAt = now.plus(properties.accessTokenTtl());
        var claims = JwtClaimsSet.builder().issuer(properties.issuer())
                .subject(userId.toString()).audience(List.of(properties.audience())).issuedAt(now)
                .notBefore(now).expiresAt(expiresAt).id(UUID.randomUUID().toString())
                .claim("sid", loginGrantId.toString()).claim("ver", securityVersion)
                .claim("permissions", permissions.stream().sorted().toList()).build();
        var header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        var encoded = encoder.encode(JwtEncoderParameters.from(header, claims));
        return new IssuedAccessToken(encoded.getTokenValue(), "Bearer", expiresAt,
                properties.accessTokenTtl().toSeconds());
    }
}
