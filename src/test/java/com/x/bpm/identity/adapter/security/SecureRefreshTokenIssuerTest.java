package com.x.bpm.identity.adapter.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureRefreshTokenIssuerTest {
    @Test
    void createsUrlSafe256BitTokensAndStoresOnlyTheirSha256Hash() throws Exception {
        var issuer = new SecureRefreshTokenIssuer();

        var first = issuer.issue();
        var second = issuer.issue();

        assertThat(first.rawToken()).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(second.rawToken());
        var expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(first.rawToken().getBytes(StandardCharsets.US_ASCII)));
        assertThat(first.tokenHash()).isEqualTo(expectedHash);
        assertThat(issuer.hash(first.rawToken())).isEqualTo(expectedHash);
        assertThat(first.toString()).doesNotContain(first.rawToken()).doesNotContain(first.tokenHash());
    }
}
