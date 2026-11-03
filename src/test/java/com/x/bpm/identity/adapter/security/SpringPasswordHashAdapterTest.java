package com.x.bpm.identity.adapter.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import static org.assertj.core.api.Assertions.assertThat;

class SpringPasswordHashAdapterTest {
    private final SpringPasswordHashAdapter adapter = new SpringPasswordHashAdapter(
            PasswordEncoderFactories.createDelegatingPasswordEncoder());

    @Test
    void hashesWithDelegatingPrefixAndMatchesOnlyTheOriginalPassword() {
        var rawPassword = "correct horse battery staple";
        var encoded = adapter.hash(rawPassword);

        assertThat(encoded).startsWith("{bcrypt}").doesNotContain(rawPassword);
        assertThat(adapter.matches(rawPassword, encoded)).isTrue();
        assertThat(adapter.matches("another long password", encoded)).isFalse();
    }
}
