package com.x.bpm.identity.adapter.security;

import com.x.bpm.identity.application.IssuedRefreshToken;
import com.x.bpm.identity.application.RefreshTokenIssuer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** Tạo 256-bit opaque refresh token và chỉ chuyển SHA-256 hash sang persistence. */
@Component
public class SecureRefreshTokenIssuer implements RefreshTokenIssuer {
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random;

    public SecureRefreshTokenIssuer() {
        this(new SecureRandom());
    }

    SecureRefreshTokenIssuer(SecureRandom random) {
        this.random = random;
    }

    @Override
    public IssuedRefreshToken issue() {
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        var rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new IssuedRefreshToken(rawToken, hash(rawToken));
    }

    @Override
    public String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("rawToken must not be blank");
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}
